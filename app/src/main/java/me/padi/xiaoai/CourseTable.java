package com.mercury.xiaoaiimport;

import java.util.ArrayList;
import java.util.List;

public class CourseTable {
    public long id;
    public String name;
    public int current;
    public String settingStr;
    public List<Long> existingCourseIds = new ArrayList<>();

    public CourseTable() {}

    public CourseTable(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        if (id == -1) return "[新建课表]";
        return name + (current == 1 ? " (当前默认)" : "");
    }
}
