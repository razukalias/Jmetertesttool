package com.razukalias.jmeter.dataengine;

import java.util.*;

public final class WorkbookModel {
    public final Map<Integer, Testcase> testcases = new LinkedHashMap<>();
    public final Map<String, DataRow> rows = new LinkedHashMap<>();
    public final Map<String, List<Assertion>> assertions = new HashMap<>();
    public final Set<String> environments = new LinkedHashSet<>();

    public static final class Testcase {
        public int index;
        public String name;
        public final List<Step> steps = new ArrayList<>();
    }
    public static final class Step {
        public int testcaseIndex;
        public String testcase;
        public String name;
        public int sequence;
        public String type = "HTTP";
        public boolean enabled = true;
        public String template = "";
        public String stepConfig = "{}";
        public String url = "";
        public String verb = "POST";
        public String contentType = "application/json";
        public int waitMs;
        public String expectedStatus = "";
        public boolean assertEnabled = true;
        public final Map<String,String> headers = new LinkedHashMap<>();
        public final List<String> environments = new ArrayList<>();
    }
    public static final class DataRow {
        public int testcaseIndex;
        public int excelRow;
        public String testcase;
        public String dataId;
        public final Map<String,String> variables = new LinkedHashMap<>();
    }
    public static final class Assertion {
        public String step;
        public String path;
        public String verb = "eq";
        public String expected = "";
    }
}
