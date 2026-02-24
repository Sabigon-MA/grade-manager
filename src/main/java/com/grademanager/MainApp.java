package com.grademanager;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainApp extends Application {

    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private TableView<Student> tableView;
    private Label statsLabel;

    // 科目名 → 総授業日数
    private final Map<String, Integer> subjectTotalDays = new LinkedHashMap<>();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("📚 成績管理アプリ");

        // 初期科目設定
        subjectTotalDays.put("数学", 20);
        subjectTotalDays.put("英語", 18);
        subjectTotalDays.put("国語", 20);
        subjectTotalDays.put("理科", 16);
        subjectTotalDays.put("社会", 15);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f4f8;");
        root.setTop(createHeader());
        root.setCenter(createMainContent());
        root.setBottom(createBottomBar());

        addSampleData();

        Scene scene = new Scene(root, 1280, 740);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(580);
        primaryStage.show();
    }

    // ═══════════════════════ Header ═══════════════════════

    private Node createHeader() {
        HBox header = new HBox(12);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color: #2c3e50;");
        header.setAlignment(Pos.CENTER_LEFT);

        Text icon = new Text("📚");
        icon.setFont(Font.font(28));

        VBox titleBox = new VBox(3);
        Text title = new Text("成績管理システム");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setFill(Color.WHITE);
        Text subtitle = new Text("総合点 ＝ 出席点（出席日数÷総授業日数×100）×50% ＋ テスト点×50%　／　出席率80%未満・総合59点以下 → 不可");
        subtitle.setFont(Font.font(11));
        subtitle.setFill(Color.web("#95a5a6"));
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(icon, titleBox);
        return header;
    }

    // ═══════════════════════ Main Content ═══════════════════════

    private Node createMainContent() {
        SplitPane split = new SplitPane();
        split.setStyle("-fx-background-color: transparent;");
        split.setPadding(new Insets(12));
        split.getItems().addAll(createTableSection(), createStatsPanel());
        split.setDividerPositions(0.73);
        return split;
    }

    // ═══════════════════════ Table Section ═══════════════════════

    private VBox createTableSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(8));

        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button addBtn      = createButton("＋ 生徒追加",       "#27ae60");
        Button editBtn     = createButton("✏ 成績編集",        "#2980b9");
        Button subjectBtn  = createButton("⚙ 科目管理",        "#e67e22");
        Button deleteBtn   = createButton("✕ 削除",            "#e74c3c");
        Button exportBtn   = createButton("⬇ CSVエクスポート", "#8e44ad");

        addBtn.setOnAction(e     -> showAddStudentDialog());
        editBtn.setOnAction(e    -> showEditGradesDialog());
        subjectBtn.setOnAction(e -> showSubjectManagerDialog());
        deleteBtn.setOnAction(e  -> deleteSelectedStudent());
        exportBtn.setOnAction(e  -> exportToCsv());

        toolbar.getChildren().addAll(addBtn, editBtn, subjectBtn, deleteBtn,
                new Separator(Orientation.VERTICAL), exportBtn);

        tableView = buildTable();
        box.getChildren().addAll(toolbar, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return box;
    }

    @SuppressWarnings("unchecked")
    private TableView<Student> buildTable() {
        TableView<Student> tv = new TableView<>(students);
        tv.setStyle("-fx-background-color: white; -fx-border-color: #dce1e7; " +
                    "-fx-border-radius: 6; -fx-background-radius: 6;");
        tv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Student, String> idCol = new TableColumn<>("学籍番号");
        idCol.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        idCol.setPrefWidth(90); idCol.setMinWidth(80);

        TableColumn<Student, String> nameCol = new TableColumn<>("氏名");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(100); nameCol.setMinWidth(80);

        tv.getColumns().addAll(idCol, nameCol);

        for (String subject : subjectTotalDays.keySet()) {
            tv.getColumns().add(buildSubjectGroup(subject));
        }

        // 総合平均
        TableColumn<Student, String> avgCol = new TableColumn<>("総合平均");
        avgCol.setCellValueFactory(data ->
            new SimpleStringProperty(formatScore(data.getValue().getOverallAverage())));
        avgCol.setCellFactory(c -> scoreCellFactory(true));
        avgCol.setPrefWidth(72); avgCol.setMinWidth(65);

        // 全体評価
        TableColumn<Student, String> gradeCol = new TableColumn<>("評価");
        gradeCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getOverallGradeLabel()));
        gradeCol.setCellFactory(c -> gradeCellFactory());
        gradeCol.setPrefWidth(65); gradeCol.setMinWidth(55);

        tv.getColumns().addAll(avgCol, gradeCol);

        tv.setRowFactory(t -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) showEditGradesDialog(); });
            return row;
        });

        tv.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateStats());
        students.addListener((ListChangeListener<Student>) c -> updateStats());
        return tv;
    }

    /** 科目グループ列: 出席日数 / 出席率 / テスト / 総合 / 評価 */
    @SuppressWarnings("unchecked")
    private TableColumn<Student, ?> buildSubjectGroup(String subject) {
        int totalDays = subjectTotalDays.getOrDefault(subject, 0);
        TableColumn<Student, String> group = new TableColumn<>(subject + "（全" + totalDays + "回）");

        // 出席日数
        TableColumn<Student, String> daysCol = new TableColumn<>("出席日数");
        daysCol.setCellValueFactory(data -> {
            Student.SubjectRecord r = data.getValue().getRecord(subject);
            if (r == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(r.attendedDays + "/" + r.totalDays);
        });
        daysCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) { setText(item); setStyle("-fx-text-fill:#bdc3c7;"); setAlignment(Pos.CENTER); return; }
                setText(item);
                setAlignment(Pos.CENTER);
                // 8割未満なら赤
                Student st = getTableView().getItems().get(getIndex());
                Student.SubjectRecord r = st.getRecord(subject);
                if (r != null && !r.hasSufficientAttendance())
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                else
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            }
        });
        daysCol.setPrefWidth(72); daysCol.setMinWidth(65);

        // 出席率
        TableColumn<Student, String> rateCol = new TableColumn<>("出席率");
        rateCol.setCellValueFactory(data -> {
            Student.SubjectRecord r = data.getValue().getRecord(subject);
            if (r == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(String.format("%.0f%%", r.attendanceRate() * 100));
        });
        rateCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) { setText(item); setStyle("-fx-text-fill:#bdc3c7;"); setAlignment(Pos.CENTER); return; }
                setText(item);
                setAlignment(Pos.CENTER);
                Student st = getTableView().getItems().get(getIndex());
                Student.SubjectRecord r = st.getRecord(subject);
                if (r != null && !r.hasSufficientAttendance())
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                else
                    setStyle("-fx-text-fill: #27ae60;");
            }
        });
        rateCol.setPrefWidth(60); rateCol.setMinWidth(55);

        // テスト点
        TableColumn<Student, String> testCol = new TableColumn<>("テスト");
        testCol.setCellValueFactory(data -> {
            Student.SubjectRecord r = data.getValue().getRecord(subject);
            if (r == null || r.testScore == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(String.format("%.0f", r.testScore));
        });
        testCol.setCellFactory(c -> scoreCellFactory(false));
        testCol.setPrefWidth(55); testCol.setMinWidth(50);

        // 総合点
        TableColumn<Student, String> compCol = new TableColumn<>("総合");
        compCol.setCellValueFactory(data -> {
            Student.SubjectRecord r = data.getValue().getRecord(subject);
            if (r == null) return new SimpleStringProperty("-");
            Double comp = r.compositeScore();
            return new SimpleStringProperty(comp != null ? formatScore(comp) : "-");
        });
        compCol.setCellFactory(c -> scoreCellFactory(true));
        compCol.setPrefWidth(58); compCol.setMinWidth(52);

        // 科目評価
        TableColumn<Student, String> gradeCol = new TableColumn<>("評価");
        gradeCol.setCellValueFactory(data -> {
            Student.SubjectRecord r = data.getValue().getRecord(subject);
            if (r == null) return new SimpleStringProperty("-");
            return new SimpleStringProperty(r.gradeLabel());
        });
        gradeCol.setCellFactory(c -> gradeCellFactory());
        gradeCol.setPrefWidth(65); gradeCol.setMinWidth(55);

        group.getColumns().addAll(daysCol, rateCol, testCol, compCol, gradeCol);
        return group;
    }

    // ═══════════════════════ Stats Panel ═══════════════════════

    private VBox createStatsPanel() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(8, 4, 8, 8));
        box.setPrefWidth(270); box.setMinWidth(210);

        Text title = new Text("📊 統計情報");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setFill(Color.web("#2c3e50"));

        statsLabel = new Label("生徒を選択してください");
        statsLabel.setWrapText(true);
        statsLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #333; -fx-font-family: monospace;");

        VBox statsCard = new VBox(8);
        statsCard.setPadding(new Insets(12));
        statsCard.setStyle("-fx-background-color: white; -fx-border-color: #dce1e7; -fx-border-radius: 8; -fx-background-radius: 8;");
        statsCard.getChildren().add(statsLabel);

        // 評価基準
        Text legendTitle = new Text("📋 評価基準");
        legendTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        legendTitle.setFill(Color.web("#2c3e50"));

        VBox legendCard = new VBox(5);
        legendCard.setPadding(new Insets(12));
        legendCard.setStyle("-fx-background-color: white; -fx-border-color: #dce1e7; -fx-border-radius: 8; -fx-background-radius: 8;");
        String[][] grades = {
            {"秀",  "90点以上",  "#8e44ad"},
            {"優",  "80〜89点", "#27ae60"},
            {"良",  "70〜79点", "#2980b9"},
            {"可",  "60〜69点", "#f39c12"},
            {"不可","59点以下",  "#e74c3c"},
        };
        for (String[] g : grades) {
            HBox row = new HBox(8); row.setAlignment(Pos.CENTER_LEFT);
            Label gl = new Label(g[0]); gl.setMinWidth(32);
            gl.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: " + g[2] + ";");
            Label dl = new Label(g[1]);
            dl.setStyle("-fx-font-size: 12; -fx-text-fill: #555;");
            row.getChildren().addAll(gl, dl);
            legendCard.getChildren().add(row);
        }

        // ルール補足
        VBox ruleCard = new VBox(5);
        ruleCard.setPadding(new Insets(10));
        ruleCard.setStyle("-fx-background-color: #fef9e7; -fx-border-color: #f9ca24; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label ruleTitle = new Label("⚠ 不可の条件");
        ruleTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        Label rule1 = new Label("① 出席率 80% 未満");
        rule1.setStyle("-fx-font-size: 12; -fx-text-fill: #e74c3c;");
        Label rule2 = new Label("② 総合点 59点以下");
        rule2.setStyle("-fx-font-size: 12; -fx-text-fill: #e74c3c;");
        ruleCard.getChildren().addAll(ruleTitle, rule1, rule2);

        // 計算式
        VBox formulaCard = new VBox(4);
        formulaCard.setPadding(new Insets(10));
        formulaCard.setStyle("-fx-background-color: #eaf4fb; -fx-border-color: #aed6f1; -fx-border-radius: 8; -fx-background-radius: 8;");
        Label ft = new Label("📐 総合点の計算");
        ft.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        Label fl = new Label("出席点 = 出席日数÷総日数×100\n出席点×50% ＋ テスト×50%\n　　　　　　 ＝ 総合点（満点100）");
        fl.setStyle("-fx-font-size: 11; -fx-text-fill: #1a5276;");
        formulaCard.getChildren().addAll(ft, fl);

        box.getChildren().addAll(title, statsCard, legendTitle, legendCard, ruleCard, formulaCard);
        return box;
    }

    // ═══════════════════════ Bottom Bar ═══════════════════════

    private Node createBottomBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(8, 20, 8, 20));
        bar.setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0;");
        bar.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("💡 行をダブルクリックで成績編集 ／ 赤字＝出席不足（8割未満）");
        hint.setStyle("-fx-font-size: 12; -fx-text-fill: #7f8c8d;");
        bar.getChildren().add(hint);
        return bar;
    }

    // ═══════════════════════ Dialogs ═══════════════════════

    /** 生徒追加ダイアログ */
    private void showAddStudentDialog() {
        Dialog<Student> dialog = new Dialog<>();
        dialog.setTitle("生徒を追加");
        dialog.setHeaderText("新しい生徒の情報を入力してください");

        ButtonType okBtn = new ButtonType("追加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField idField   = new TextField("S" + String.format("%03d", students.size() + 1));
        TextField nameField = new TextField(); nameField.setPromptText("例：山田 太郎");

        grid.add(new Label("学籍番号:"), 0, 0); grid.add(idField, 1, 0);
        grid.add(new Label("氏名:"),     0, 1); grid.add(nameField, 1, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == okBtn && !nameField.getText().trim().isEmpty())
                return new Student(idField.getText().trim(), nameField.getText().trim());
            return null;
        });

        dialog.showAndWait().ifPresent(s -> {
            students.add(s);
            tableView.getSelectionModel().select(s);
            showEditGradesDialog();
        });
    }

    /** 成績編集ダイアログ（出席日数 + テスト点） */
    private void showEditGradesDialog() {
        Student sel = tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("生徒を選択してください", Alert.AlertType.INFORMATION); return; }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("成績編集 — " + sel.getName());
        dialog.setHeaderText("各科目の出席日数とテスト点を入力してください");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox container = new VBox(10);
        container.setPadding(new Insets(16));

        // ヘッダー行
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(9);

        // 列ヘッダー
        grid.add(boldLabel("科目",      90),  0, 0);
        grid.add(boldLabel("総授業数",  70),  1, 0);
        grid.add(boldLabel("出席日数",  70),  2, 0);
        grid.add(boldLabel("出席率",    60),  3, 0);
        grid.add(boldLabel("出席点",    60),  4, 0);
        grid.add(boldLabel("テスト点",  70),  5, 0);
        grid.add(boldLabel("総合点",    60),  6, 0);
        grid.add(boldLabel("評価",      55),  7, 0);
        grid.add(new Separator(), 0, 1, 8, 1);

        // 科目行
        int[] rowIdx = {2};
        Map<String, TextField[]> fieldMap = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : subjectTotalDays.entrySet()) {
            String subject  = entry.getKey();
            int    total    = entry.getValue();
            Student.SubjectRecord rec = sel.getOrCreateRecord(subject, total);

            Label subjectLbl = new Label(subject);
            subjectLbl.setMinWidth(90);

            Label totalLbl = new Label(String.valueOf(total) + "回");
            totalLbl.setMinWidth(60); totalLbl.setAlignment(Pos.CENTER);

            // 出席日数入力
            TextField attendedField = new TextField(String.valueOf(rec.attendedDays));
            attendedField.setPrefWidth(65);
            attendedField.setPromptText("0〜" + total);

            // テスト点入力
            TextField testField = new TextField(rec.testScore != null ? String.format("%.0f", rec.testScore) : "");
            testField.setPrefWidth(65);
            testField.setPromptText("0〜100");

            // リアルタイム計算ラベル
            Label rateLabel  = new Label("--");  rateLabel.setMinWidth(55);  rateLabel.setAlignment(Pos.CENTER);
            Label attPtLabel = new Label("--");  attPtLabel.setMinWidth(55); attPtLabel.setAlignment(Pos.CENTER);
            Label compLabel  = new Label("--");  compLabel.setMinWidth(55);  compLabel.setAlignment(Pos.CENTER);
            Label gradeLabel = new Label("--");  gradeLabel.setMinWidth(50); gradeLabel.setAlignment(Pos.CENTER);

            Runnable updatePreview = () -> {
                try {
                    int    att  = Integer.parseInt(attendedField.getText().trim());
                    double rate = (double) att / total;
                    double attPt = rate * 100.0;
                    rateLabel.setText(String.format("%.0f%%", rate * 100));
                    attPtLabel.setText(String.format("%.1f", attPt));

                    boolean sufficient = rate >= 0.8;
                    String rateColor = sufficient ? "#27ae60" : "#e74c3c";
                    rateLabel.setStyle("-fx-text-fill: " + rateColor + "; -fx-font-weight: bold;");
                    attPtLabel.setStyle("-fx-text-fill: " + rateColor + ";");

                    String testStr = testField.getText().trim();
                    if (!testStr.isEmpty()) {
                        double test = Double.parseDouble(testStr);
                        double comp = attPt * 0.5 + test * 0.5;
                        compLabel.setText(String.format("%.1f", comp));
                        String g = sufficient ? Student.scoreToGrade(comp) : "不可(出席)";
                        gradeLabel.setText(g);
                        gradeLabel.setStyle(gradeStyle(g) + " -fx-font-weight: bold;");
                        compLabel.setStyle("-fx-font-weight: bold;");
                    } else {
                        compLabel.setText("--"); gradeLabel.setText("--");
                        compLabel.setStyle(""); gradeLabel.setStyle("");
                    }
                } catch (NumberFormatException ex) {
                    rateLabel.setText("--"); attPtLabel.setText("--");
                    compLabel.setText("--"); gradeLabel.setText("--");
                    rateLabel.setStyle(""); attPtLabel.setStyle("");
                }
            };
            attendedField.textProperty().addListener((o, ov, nv) -> updatePreview.run());
            testField.textProperty().addListener((o, ov, nv) -> updatePreview.run());
            updatePreview.run();

            grid.add(subjectLbl,    0, rowIdx[0]);
            grid.add(totalLbl,      1, rowIdx[0]);
            grid.add(attendedField, 2, rowIdx[0]);
            grid.add(rateLabel,     3, rowIdx[0]);
            grid.add(attPtLabel,    4, rowIdx[0]);
            grid.add(testField,     5, rowIdx[0]);
            grid.add(compLabel,     6, rowIdx[0]);
            grid.add(gradeLabel,    7, rowIdx[0]);

            fieldMap.put(subject, new TextField[]{attendedField, testField});
            rowIdx[0]++;
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true); scroll.setPrefHeight(340);
        scroll.setStyle("-fx-background-color: transparent;");

        container.getChildren().add(scroll);
        dialog.getDialogPane().setContent(container);
        dialog.getDialogPane().setPrefWidth(680);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                for (Map.Entry<String, TextField[]> e : fieldMap.entrySet()) {
                    String subject = e.getKey();
                    int total = subjectTotalDays.getOrDefault(subject, 0);
                    Student.SubjectRecord rec = sel.getOrCreateRecord(subject, total);
                    try {
                        int att = Integer.parseInt(e.getValue()[0].getText().trim());
                        rec.attendedDays = Math.max(0, Math.min(total, att));
                    } catch (NumberFormatException ignored) {}
                    String testStr = e.getValue()[1].getText().trim();
                    if (!testStr.isEmpty()) {
                        try {
                            rec.testScore = Math.max(0, Math.min(100, Double.parseDouble(testStr)));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        rec.testScore = null;
                    }
                }
                tableView.refresh();
                updateStats();
            }
            return null;
        });

        dialog.showAndWait();
    }

    /** 科目管理ダイアログ（科目の追加・削除・授業日数変更） */
    private void showSubjectManagerDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("科目管理");
        dialog.setHeaderText("科目の追加・削除・総授業日数の変更");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox container = new VBox(10);
        container.setPadding(new Insets(16));

        // 既存科目リスト
        Label existingTitle = new Label("登録済み科目");
        existingTitle.setStyle("-fx-font-weight: bold;");

        GridPane existingGrid = new GridPane();
        existingGrid.setHgap(10); existingGrid.setVgap(6);
        existingGrid.add(boldLabel("科目名", 100), 0, 0);
        existingGrid.add(boldLabel("総授業日数", 90), 1, 0);
        existingGrid.add(new Separator(), 0, 1, 3, 1);

        Map<String, TextField> dayFields = new LinkedHashMap<>();
        int[] r = {2};
        for (Map.Entry<String, Integer> e : subjectTotalDays.entrySet()) {
            Label lbl = new Label(e.getKey()); lbl.setMinWidth(100);
            TextField tf = new TextField(String.valueOf(e.getValue())); tf.setPrefWidth(80);
            Button delBtn = createButton("削除", "#e74c3c");
            String subName = e.getKey();
            delBtn.setOnAction(ev -> {
                subjectTotalDays.remove(subName);
                existingGrid.getChildren().removeIf(n -> {
                    Integer ri = GridPane.getRowIndex(n);
                    return ri != null && ri == GridPane.getRowIndex(lbl);
                });
            });
            existingGrid.add(lbl,   0, r[0]);
            existingGrid.add(tf,    1, r[0]);
            existingGrid.add(delBtn,2, r[0]);
            dayFields.put(e.getKey(), tf);
            r[0]++;
        }

        // 新規追加
        Separator sep = new Separator();
        Label addTitle = new Label("新規科目を追加");
        addTitle.setStyle("-fx-font-weight: bold;");
        HBox addRow = new HBox(8); addRow.setAlignment(Pos.CENTER_LEFT);
        TextField newNameField = new TextField(); newNameField.setPromptText("科目名"); newNameField.setPrefWidth(120);
        TextField newDaysField = new TextField(); newDaysField.setPromptText("総授業日数"); newDaysField.setPrefWidth(90);
        Button addBtn = createButton("追加", "#27ae60");
        addRow.getChildren().addAll(newNameField, new Label("全"), newDaysField, new Label("回"), addBtn);

        addBtn.setOnAction(ev -> {
            String nm = newNameField.getText().trim();
            String ds = newDaysField.getText().trim();
            if (!nm.isEmpty() && !ds.isEmpty() && !subjectTotalDays.containsKey(nm)) {
                try {
                    int days = Integer.parseInt(ds);
                    if (days > 0) {
                        subjectTotalDays.put(nm, days);
                        newNameField.clear(); newDaysField.clear();
                        showAlert("「" + nm + "」を追加しました。科目一覧はOK後に反映されます。", Alert.AlertType.INFORMATION);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        container.getChildren().addAll(existingTitle, existingGrid, sep, addTitle, addRow);
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true); scroll.setPrefHeight(360);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(420);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                // 日数の変更を反映
                for (Map.Entry<String, TextField> e : dayFields.entrySet()) {
                    if (subjectTotalDays.containsKey(e.getKey())) {
                        try {
                            int d = Integer.parseInt(e.getValue().getText().trim());
                            if (d > 0) subjectTotalDays.put(e.getKey(), d);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                rebuildTable();
            }
            return null;
        });
        dialog.showAndWait();
    }

    private void deleteSelectedStudent() {
        Student sel = tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("生徒を選択してください", Alert.AlertType.INFORMATION); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            sel.getName() + " を削除しますか？", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("削除確認");
        confirm.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> students.remove(sel));
    }

    // ═══════════════════════ CSV Export ═══════════════════════

    private void exportToCsv() {
        if (students.isEmpty()) { showAlert("データがありません", Alert.AlertType.INFORMATION); return; }

        FileChooser fc = new FileChooser();
        fc.setTitle("CSVファイルを保存");
        fc.setInitialFileName("grades_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(tableView.getScene().getWindow());

        if (file == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            pw.print('\uFEFF'); // BOM for Excel

            // ヘッダー
            StringBuilder hdr = new StringBuilder("学籍番号,氏名");
            for (Map.Entry<String, Integer> e : subjectTotalDays.entrySet()) {
                String s = e.getKey();
                hdr.append(",").append(s).append("_総授業数");
                hdr.append(",").append(s).append("_出席日数");
                hdr.append(",").append(s).append("_出席率(%)");
                hdr.append(",").append(s).append("_出席点");
                hdr.append(",").append(s).append("_テスト点");
                hdr.append(",").append(s).append("_総合点");
                hdr.append(",").append(s).append("_評価");
            }
            hdr.append(",総合平均,全体評価");
            pw.println(hdr);

            // データ行
            for (Student st : students) {
                StringBuilder row = new StringBuilder(st.getStudentId()).append(",").append(st.getName());
                for (Map.Entry<String, Integer> e : subjectTotalDays.entrySet()) {
                    String subject = e.getKey();
                    Student.SubjectRecord rec = st.getRecord(subject);
                    if (rec == null) {
                        row.append(",").append(e.getValue()).append(",-,-,-,-,-,-");
                    } else {
                        Double comp = rec.compositeScore();
                        row.append(",").append(rec.totalDays);
                        row.append(",").append(rec.attendedDays);
                        row.append(",").append(String.format("%.1f", rec.attendanceRate() * 100));
                        row.append(",").append(String.format("%.1f", rec.attendanceScore()));
                        row.append(",").append(rec.testScore != null ? String.format("%.0f", rec.testScore) : "");
                        row.append(",").append(comp != null ? String.format("%.1f", comp) : "");
                        row.append(",").append(rec.gradeLabel());
                    }
                }
                row.append(",").append(String.format("%.1f", st.getOverallAverage()));
                row.append(",").append(st.getOverallGradeLabel());
                pw.println(row);
            }
            showAlert("CSVエクスポート完了！\n保存先: " + file.getAbsolutePath(), Alert.AlertType.INFORMATION);
        } catch (Exception ex) {
            showAlert("エクスポートに失敗しました: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ═══════════════════════ Stats Update ═══════════════════════

    private void updateStats() {
        if (students.isEmpty()) { statsLabel.setText("生徒が登録されていません"); return; }

        Student sel = tableView.getSelectionModel().getSelectedItem();
        StringBuilder sb = new StringBuilder();

        double classAvg = students.stream().mapToDouble(Student::getOverallAverage).average().orElse(0);
        sb.append("👥 全体統計\n");
        sb.append(String.format("  生徒数: %d名\n", students.size()));
        sb.append(String.format("  クラス平均: %.1f点\n\n", classAvg));

        sb.append("📊 評価分布\n");
        for (String g : new String[]{"秀","優","良","可","不可"}) {
            long cnt = students.stream().filter(s -> s.getOverallGradeLabel().equals(g)).count();
            if (cnt > 0) sb.append(String.format("  %s: %d名\n", g, cnt));
        }

        if (sel != null && !sel.getSubjectMap().isEmpty()) {
            sb.append("\n─────────────────\n");
            sb.append("👤 ").append(sel.getName()).append("\n");
            sb.append(String.format("  総合平均: %.1f点\n", sel.getOverallAverage()));
            sb.append("  評価: ").append(sel.getOverallGradeLabel()).append("\n\n");
            sb.append("📝 科目別\n");
            for (Map.Entry<String, Student.SubjectRecord> e : sel.getSubjectMap().entrySet()) {
                Student.SubjectRecord r = e.getValue();
                Double comp = r.compositeScore();
                sb.append(String.format("  %s\n", e.getKey()));
                sb.append(String.format("    出席: %d/%d回（%.0f%%）\n",
                        r.attendedDays, r.totalDays, r.attendanceRate() * 100));
                sb.append(String.format("    出席点: %.1f  テスト: %s\n",
                        r.attendanceScore(),
                        r.testScore != null ? String.format("%.0f", r.testScore) : "-"));
                sb.append(String.format("    総合: %s  評価: %s\n",
                        comp != null ? String.format("%.1f", comp) : "-",
                        r.gradeLabel()));
            }
        }

        statsLabel.setText(sb.toString());
    }

    // ═══════════════════════ Table Rebuild ═══════════════════════

    @SuppressWarnings("unchecked")
    private void rebuildTable() {
        tableView.getColumns().clear();

        TableColumn<Student, String> idCol = new TableColumn<>("学籍番号");
        idCol.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        idCol.setPrefWidth(90);

        TableColumn<Student, String> nameCol = new TableColumn<>("氏名");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(100);

        tableView.getColumns().addAll(idCol, nameCol);
        for (String subject : subjectTotalDays.keySet())
            tableView.getColumns().add(buildSubjectGroup(subject));

        TableColumn<Student, String> avgCol = new TableColumn<>("総合平均");
        avgCol.setCellValueFactory(data ->
            new SimpleStringProperty(formatScore(data.getValue().getOverallAverage())));
        avgCol.setCellFactory(c -> scoreCellFactory(true));
        avgCol.setPrefWidth(72);

        TableColumn<Student, String> gradeCol = new TableColumn<>("評価");
        gradeCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getOverallGradeLabel()));
        gradeCol.setCellFactory(c -> gradeCellFactory());
        gradeCol.setPrefWidth(65);

        tableView.getColumns().addAll(avgCol, gradeCol);
        tableView.refresh();
    }

    // ═══════════════════════ Cell Factories ═══════════════════════

    private TableCell<Student, String> scoreCellFactory(boolean bold) {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) {
                    setText(item != null ? item : null);
                    setStyle("-fx-text-fill: #bdc3c7;"); setAlignment(Pos.CENTER); return;
                }
                setText(item); setAlignment(Pos.CENTER);
                try {
                    double v = Double.parseDouble(item.replace("%",""));
                    String color = v >= 90 ? "#8e44ad" : v >= 80 ? "#27ae60" :
                                   v >= 70 ? "#2980b9" : v >= 60 ? "#f39c12" : "#e74c3c";
                    setStyle("-fx-text-fill:" + color + ";" + (bold ? "-fx-font-weight:bold;" : ""));
                } catch (NumberFormatException ex) { setStyle(""); }
            }
        };
    }

    private TableCell<Student, String> gradeCellFactory() {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) {
                    setText(item); setStyle(""); setAlignment(Pos.CENTER); return;
                }
                setText(item); setAlignment(Pos.CENTER);
                setStyle(gradeStyle(item) + " -fx-font-weight: bold;");
            }
        };
    }

    // ═══════════════════════ Helpers ═══════════════════════

    private Button createButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                     "-fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 5 11;");
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e  -> btn.setOpacity(1.0));
        return btn;
    }

    private Label boldLabel(String text, double width) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 12;");
        l.setMinWidth(width);
        return l;
    }

    private void showAlert(String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setTitle(type == Alert.AlertType.ERROR ? "エラー" : "情報");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private String formatScore(double v) { return String.format("%.1f", v); }

    private String gradeStyle(String g) {
        return switch (g) {
            case "秀"       -> "-fx-text-fill: #8e44ad;";
            case "優"       -> "-fx-text-fill: #27ae60;";
            case "良"       -> "-fx-text-fill: #2980b9;";
            case "可"       -> "-fx-text-fill: #f39c12;";
            default         -> "-fx-text-fill: #e74c3c;"; // 不可・不可(出席)
        };
    }

    // ═══════════════════════ Sample Data ═══════════════════════

    private void addSampleData() {
        // {出席日数, テスト点} × 科目順 [数学20, 英語18, 国語20, 理科16, 社会15]
        Object[][] data = {
            {"S001", "山田 太郎",  new int[][]{{18,85},{15,90},{17,80},{13,88},{12,75}}},
            {"S002", "鈴木 花子",  new int[][]{{20,95},{18,98},{19,92},{16,90},{15,97}}},
            {"S003", "佐藤 健",    new int[][]{{14,55},{10,60},{15,65},{9, 50},{11,58}}}, // 英語・理科出席不足
            {"S004", "田中 美咲",  new int[][]{{19,78},{17,82},{18,88},{14,76},{13,80}}},
            {"S005", "渡辺 悠斗",  new int[][]{{16,62},{14,70},{18,68},{12,58},{12,65}}},
        };
        String[] subjectNames = subjectTotalDays.keySet().toArray(new String[0]);
        for (Object[] row : data) {
            Student st = new Student((String) row[0], (String) row[1]);
            int[][] scores = (int[][]) row[2];
            for (int i = 0; i < subjectNames.length; i++) {
                int total = subjectTotalDays.get(subjectNames[i]);
                Student.SubjectRecord rec = st.getOrCreateRecord(subjectNames[i], total);
                rec.attendedDays = scores[i][0];
                rec.testScore    = (double) scores[i][1];
            }
            students.add(st);
        }
    }

    public static void main(String[] args) { launch(args); }
}
