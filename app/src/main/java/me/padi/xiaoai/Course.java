package com.mercury.xiaoaiimport;

public class Course {
    public String name;
    public String teacher;
    public String position;
    public int day;
    public String sections;
    public String weeks;
    public boolean isCustomTime = false;
    public String customStartTime = "";
    public String customEndTime = "";
    public boolean hasExplicitSectionRange = false;

    public boolean isInvalid = false;
    public boolean nameInvalid = false;
    public boolean dayInvalid = false;
    public boolean sectionsInvalid = false;
    public boolean weeksInvalid = false;
    public String invalidReason = "";

    public String originalSections = "";
    public String originalWeeks = "";
    public boolean isAutoCorrected = false;
    public String autoCorrectedReason = "";

    public void sanitizeAndValidate() {
        isInvalid = false;
        nameInvalid = false;
        dayInvalid = false;
        sectionsInvalid = false;
        weeksInvalid = false;
        
        isAutoCorrected = false;
        autoCorrectedReason = "";
        
        StringBuilder reason = new StringBuilder();

        if (name == null || name.trim().isEmpty()) {
            nameInvalid = true;
            isInvalid = true;
            reason.append("名称为空；");
        }
        if (day < 1 || day > 7) {
            dayInvalid = true;
            isInvalid = true;
            reason.append("星期不在1-7之间；");
        }
        
        if (sections == null || sections.trim().isEmpty()) {
            sectionsInvalid = true;
            isInvalid = true;
            reason.append("节次为空；");
        } else {
            originalSections = sections.trim();
            String parsedSec = parseNumberList(originalSections);
            if (parsedSec.isEmpty()) {
                sectionsInvalid = true;
                isInvalid = true;
                reason.append("节次无法提取数字: ").append(sections).append("；");
            } else {
                sections = parsedSec;
                if (!isStandardFormat(originalSections) && !originalSections.equals(parsedSec)) {
                    isAutoCorrected = true;
                    autoCorrectedReason += "节次 [" + originalSections + "] -> [" + parsedSec + "]；";
                }
            }
        }

        if (weeks == null || weeks.trim().isEmpty()) {
            weeksInvalid = true;
            isInvalid = true;
            reason.append("周次为空；");
        } else {
            originalWeeks = weeks.trim();
            String parsedWeeks = parseNumberList(originalWeeks);
            if (parsedWeeks.isEmpty()) {
                weeksInvalid = true;
                isInvalid = true;
                reason.append("周次无法提取数字: ").append(weeks).append("；");
            } else {
                weeks = parsedWeeks;
                if (!isStandardFormat(originalWeeks) && !originalWeeks.equals(parsedWeeks)) {
                    isAutoCorrected = true;
                    autoCorrectedReason += "周次 [" + originalWeeks + "] -> [" + parsedWeeks + "]；";
                }
            }
        }
        
        invalidReason = reason.toString();
    }


    private boolean isStandardFormat(String input) {
        return input != null && input.matches("^\\d+(,\\d+)*$");
    }

    private String parseNumberList(String input) {
        java.util.Set<Integer> nums = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(?:-|至|~)\\s*(\\d+)(?:[\\s周\\(（\\)）]*(单|双)[\\s周\\(（\\)）]*)?|(\\d+)").matcher(input);
        while (m.find()) {
            if (m.group(1) != null && m.group(2) != null) {
                int start = Integer.parseInt(m.group(1));
                int end = Integer.parseInt(m.group(2));
                int min = Math.min(start, end);
                int max = Math.max(start, end);
                String type = m.group(3);
                int actMin = min;
                int step = 1;
                
                if (type != null) {
                    if (type.equals("单")) {
                        if (actMin % 2 == 0) actMin++;
                        step = 2;
                    } else if (type.equals("双")) {
                        if (actMin % 2 != 0) actMin++;
                        step = 2;
                    }
                }
                
                for (int i = actMin; i <= max; i += step) {
                    nums.add(i);
                }
            } else if (m.group(4) != null) {
                nums.add(Integer.parseInt(m.group(4)));
            }
        }
        if (nums.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int n : nums) {
            sb.append(n).append(",");
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}
