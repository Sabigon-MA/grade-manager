package com.grademanager;

import javafx.beans.property.*;
import java.util.*;

/**
 * 生徒モデル
 *
 * 成績計算仕様:
 *   出席点   = 出席日数 ÷ 総授業日数 × 100  (0〜100点)
 *   テスト点 = 100点満点
 *   総合点   = 出席点 × 50% + テスト点 × 50% (満点100点)
 *
 * 不可条件:
 *   (1) 出席率 < 80%  → 出席不足で自動不可
 *   (2) 総合点 <= 59  → 点数不足で不可
 */
public class Student {

    private final StringProperty name;
    private final StringProperty studentId;

    /** 科目名 → SubjectRecord */
    private final Map<String, SubjectRecord> subjectMap;

    // ── 科目データ ────────────────────────────────────────────────

    public static class SubjectRecord {
        /** 総授業日数（科目ごとに設定） */
        public int totalDays;
        /** 出席日数 */
        public int attendedDays;
        /** テスト点（0〜100） */
        public Double testScore;

        public SubjectRecord(int totalDays) {
            this.totalDays = totalDays;
            this.attendedDays = 0;
            this.testScore = null;
        }

        /** 出席率 (0.0〜1.0) */
        public double attendanceRate() {
            if (totalDays <= 0) return 0.0;
            return (double) attendedDays / totalDays;
        }

        /** 出席点 (0〜100点換算) */
        public double attendanceScore() {
            return attendanceRate() * 100.0;
        }

        /** 出席が8割以上か */
        public boolean hasSufficientAttendance() {
            return attendanceRate() >= 0.8;
        }

        /**
         * 総合点 = 出席点×0.5 + テスト点×0.5
         * 出席不足 or テスト未入力の場合は null
         */
        public Double compositeScore() {
            if (testScore == null) return null;
            return attendanceScore() * 0.5 + testScore * 0.5;
        }

        /**
         * 評価文字列
         * 出席不足 → "不可(出席)"
         * 総合点59以下 → "不可"
         * それ以外 → 秀/優/良/可
         */
        public String gradeLabel() {
            if (!hasSufficientAttendance()) return "不可(出席)";
            Double comp = compositeScore();
            if (comp == null) return "-";
            return Student.scoreToGrade(comp);
        }
    }

    // ── コンストラクタ ────────────────────────────────────────────

    public Student(String studentId, String name) {
        this.studentId = new SimpleStringProperty(studentId);
        this.name = new SimpleStringProperty(name);
        this.subjectMap = new LinkedHashMap<>();
    }

    // ── プロパティ ────────────────────────────────────────────────

    public String getName() { return name.get(); }
    public void setName(String n) { name.set(n); }
    public StringProperty nameProperty() { return name; }

    public String getStudentId() { return studentId.get(); }
    public void setStudentId(String id) { studentId.set(id); }
    public StringProperty studentIdProperty() { return studentId; }

    public Map<String, SubjectRecord> getSubjectMap() { return subjectMap; }

    // ── 科目操作 ────────────────────────────────────────────────

    /** 科目を追加（総授業日数を設定） */
    public SubjectRecord getOrCreateRecord(String subject, int totalDays) {
        return subjectMap.computeIfAbsent(subject, k -> new SubjectRecord(totalDays));
    }

    public SubjectRecord getRecord(String subject) {
        return subjectMap.get(subject);
    }

    public void removeSubject(String subject) {
        subjectMap.remove(subject);
    }

    // ── 全体集計 ────────────────────────────────────────────────

    /** 全科目の総合点の平均（入力済み科目のみ） */
    public double getOverallAverage() {
        List<Double> comps = new ArrayList<>();
        for (SubjectRecord r : subjectMap.values()) {
            Double c = r.compositeScore();
            if (c != null) comps.add(c);
        }
        if (comps.isEmpty()) return 0.0;
        return comps.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public double getMaxComposite() {
        return subjectMap.values().stream()
                .map(SubjectRecord::compositeScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    public double getMinComposite() {
        return subjectMap.values().stream()
                .map(SubjectRecord::compositeScore).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    /** 全体評価（全科目の総合平均で判定） */
    public String getOverallGradeLabel() {
        // 1科目でも出席不足なら全体不可
        boolean anyAbsenceFail = subjectMap.values().stream()
                .anyMatch(r -> !r.hasSufficientAttendance());
        if (anyAbsenceFail) return "不可";
        return scoreToGrade(getOverallAverage());
    }

    // ── 静的ユーティリティ ────────────────────────────────────────

    /** 点数 → 評価文字列（59点以下は不可） */
    public static String scoreToGrade(double score) {
        if (score >= 90) return "秀";
        if (score >= 80) return "優";
        if (score >= 70) return "良";
        if (score >= 60) return "可";
        return "不可";   // 59点以下
    }

    @Override
    public String toString() {
        return studentId.get() + " - " + name.get();
    }
}
