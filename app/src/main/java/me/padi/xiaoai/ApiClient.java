package me.padi.xiaoai;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    public static final String PUBLIC_UA = "Mozilla/5.0 (Linux; Android 16; 23113RKC6C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.14 Mobile Safari/537.36";
    public static final String SEC_CH_UA = "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"146\", \"Chromium\";v=\"146\"";
    public static final String SEC_CH_UA_MOBILE = "?1";
    public static final String SEC_CH_UA_PLATFORM = "\"Android\"";

    private static final String APP_ID = "326813440150602752";
    private static final String BASE_URL = "https://i.ai.mi.com";
    private static final String SOURCE_NAME = "course-app-aiSchedule";
    private static final String X_REQUESTED_WITH = "com.xiaomi.aischedule";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(new CookieJar() {
                private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    cookieStore.put(url.host(), cookies);
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    List<Cookie> cookies = cookieStore.get(url.host());
                    return cookies != null ? cookies : new ArrayList<>();
                }
            })
            .build();

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    public static final String[] COLOR_PRESETS = {
            "{\"color\":\"#00A6F2\",\"background\":\"#E5F4FF\"}",
            "{\"color\":\"#FC6B50\",\"background\":\"#FDEBDE\"}",
            "{\"color\":\"#3CB3C8\",\"background\":\"#DEFBF8\"}",
            "{\"color\":\"#7D7AEA\",\"background\":\"#EDEDFF\"}",
            "{\"color\":\"#FF9900\",\"background\":\"#FCEBCD\"}",
            "{\"color\":\"#EF5B75\",\"background\":\"#FFEFF0\"}",
            "{\"color\":\"#5B8EFF\",\"background\":\"#EAF1FF\"}",
            "{\"color\":\"#F067BB\",\"background\":\"#FFEDF8\"}",
            "{\"color\":\"#29BBAA\",\"background\":\"#E2F8F3\"}",
            "{\"color\":\"#CBA713\",\"background\":\"#FFF8C8\"}",
            "{\"color\":\"#B967E3\",\"background\":\"#F9EDFF\"}",
            "{\"color\":\"#6E8ADA\",\"background\":\"#F3F2FD\"}"
    };

    public static final String SYSTEM_PROMPT =
            "你是一个专业的大学课表解析助手。\n" +
                    "从教务系统的HTML中提取信息，并以严格的JSON格式返回两部分：'courses' (必填) 和 'schedule' (选填)。格式如下：\n" +
                    "{\n" +
                    "  \"courses\": [\n" +
                    "    {\"name\":\"课程名称\",\"teacher\":\"教师姓名\",\"position\":\"上课地点\"," +
                    "\"day\":1,\"sections\":\"1,2\",\"weeks\":\"1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16\"}\n" +
                    "  ],\n" +
                    "  \"schedule\": {\n" +
                    "    \"morningNum\": 上午节数 (如 5),\n" +
                    "    \"afternoonNum\": 下午节数 (如 4),\n" +
                    "    \"nightNum\": 晚上节数 (如 3),\n" +
                    "    \"sections\": \"[{\\\"i\\\":1,\\\"s\\\":\\\"08:00\\\",\\\"e\\\":\\\"08:45\\\"},...]\"\n" +
                    "  }\n" +
                    "}\n" +
                    "rules:\n" +
                    "- day: 1=周一, 2=周二... sections: 逗号分隔如 \"1,2\"\n" +
                    "- 同一课程不同周次/不同时间必须拆成独立的course对象\n" +
                    "- schedule对象是可选的，且其内部字段（如节数和具体时间表sections）均为相互独立、按需提取的内容。即使只找到其中一个，也请把对应的字段放在schedule中返回，未找到的字段直接省略即可，切勿自行编造。如果全都没有，则可省略整个schedule对象。\n" +
                    "- schedule内的sections必须是一个转义好的、合法JSON字符串的结构。\n" +
                    "- 严禁直接照抄提示词示例(如\"[{\\\"i\\\":1,...}]\")！你必须且只能从用户提供的真实HTML源码中提取实际的时间表。如果HTML中没有时间表，必须彻底省略sections字段，绝不能使用或伪造示例数据！\n" +
                    "- 必须且仅能输出上述纯JSON结构，不可包含任何外部包裹符(如```json)或代码说明。";

    public interface ParseCallback {
        void onUpdate(String reasoning, String content);

        void onSuccess(ParseResult result);

        void onError(Exception e);
    }

    public static void parseCoursesStreaming(String html, String apiKey, String model, String baseUrl, String systemPrompt, ParseCallback callback) {
        try {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                callback.onError(new Exception("API地址为空，请在主界面(模块设置)中检查大模型API地址！"));
                return;
            }
            if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                callback.onError(new Exception("API地址格式错误(缺少http/https)，请检查配置：" + baseUrl));
                return;
            }

            if (html.length() > 150000) html = html.substring(0, 150000);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("stream", true);
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            msgs.put(new JSONObject().put("role", "user").put("content", "请解析以下课表HTML：\n\n" + html));
            body.put("messages", msgs);

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            Request req = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    .build();

            try (Response resp = CLIENT.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String raw = resp.body() != null ? resp.body().string() : "";
                    callback.onError(new Exception("API " + resp.code() + ": " + raw));
                    return;
                }

                if (resp.body() == null) {
                    callback.onError(new Exception("Empty response body"));
                    return;
                }

                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body().byteStream(), "UTF-8"));
                String line;
                StringBuilder fullContent = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    if (line.equals("data: [DONE]")) break;
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        try {
                            JSONObject json = new JSONObject(data);
                            JSONArray choices = json.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                if (delta != null) {
                                    String reasoning = delta.isNull("reasoning_content") ? "" : delta.optString("reasoning_content", "");
                                    String content = delta.isNull("content") ? "" : delta.optString("content", "");

                                    if (!content.isEmpty()) {
                                        fullContent.append(content);
                                    }
                                    if (!reasoning.isEmpty() || !content.isEmpty()) {
                                        callback.onUpdate(reasoning, content);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                String aiResponseContent = fullContent.toString().trim();
                aiResponseContent = repairAiJson(aiResponseContent);

                JSONObject fullResponseJson;
                try {
                    fullResponseJson = new JSONObject(aiResponseContent);
                } catch (JSONException e) {
                    int s = aiResponseContent.indexOf('{'), eIdx = aiResponseContent.lastIndexOf('}');
                    if (s < 0 || eIdx < 0) {
                        throw new Exception("啊哦，AI开小差了，返回了无法识别的内容（非标准JSON结构）。请尝试点击“重新解析”！");
                    }
                    try {
                        fullResponseJson = new JSONObject(aiResponseContent.substring(s, eIdx + 1));
                    } catch (JSONException ex) {
                        throw new Exception("大模型返回的JSON存在格式错误：" + translateJsonError(ex.getMessage()) + " (无法自动修复，请重试)");
                    }
                }

                ParseResult parseResult = parseJsonToResult(fullResponseJson);
                callback.onSuccess(parseResult);
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public static ParseResult parseJsonToResult(JSONObject fullResponseJson) throws Exception {
        ParseResult parseResult = new ParseResult();
        List<Course> list = new ArrayList<>();
        Map<String, String> styleMap = new HashMap<>();
        int colorIdx = 0;

        if (!fullResponseJson.has("courses")) {
            throw new Exception("缺失必填的核心大纲字段：未找到 'courses' (课程列表) 字段。模型可能未能理解或遇到了空课表。");
        }

        JSONArray arr;
        try {
            arr = fullResponseJson.getJSONArray("courses");
        } catch (JSONException e) {
            throw new Exception("数据类型不合法：'courses' 字段本该是一个列表(Array)，但模型返回了错误的数据类型。");
        }

        if (arr.length() == 0) {
            throw new Exception("提取内容为空：解析成功，但在您提供的相关文本中未能抽取出任何有效的课程数据。(长度为 0)");
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Course c = new Course();
            c.name = o.optString("name", "").trim();
            c.teacher = o.optString("teacher", "").trim();
            c.position = o.optString("position", "").trim();
            c.day = o.optInt("day", 1);
            c.sections = o.optString("sections", "1,2").trim();

            if (o.has("weeks")) {
                Object weeksObj = o.get("weeks");
                if (weeksObj instanceof JSONArray) {
                    JSONArray weeksArr = (JSONArray) weeksObj;
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < weeksArr.length(); j++) {
                        sb.append(weeksArr.getInt(j));
                        if (j < weeksArr.length() - 1) sb.append(",");
                    }
                    c.weeks = sb.toString();
                } else {
                    c.weeks = o.optString("weeks", "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16").trim();
                }
            } else {
                c.weeks = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16";
            }

            if (!c.name.isEmpty()) {
                c.sanitizeAndValidate();
                if (!styleMap.containsKey(c.name)) {
                    styleMap.put(c.name, COLOR_PRESETS[colorIdx % 12]);
                    colorIdx++;
                }
                c.style = styleMap.get(c.name);
                list.add(c);
            } else {
                c.name = "(未命名课程)";
                c.sanitizeAndValidate();
                c.isInvalid = true;
                c.nameInvalid = true;
                c.invalidReason += "必填字段: name(课程名) 缺失；";
                if (!styleMap.containsKey(c.name)) {
                    styleMap.put(c.name, COLOR_PRESETS[colorIdx % 12]);
                    colorIdx++;
                }
                c.style = styleMap.get(c.name);
                list.add(c);
            }
        }
        parseResult.courses = list;

        if (fullResponseJson.has("schedule") && !fullResponseJson.isNull("schedule")) {
            JSONObject scheduleJson = fullResponseJson.getJSONObject("schedule");
            ScheduleConfig scheduleConfig = new ScheduleConfig();
            if (scheduleJson.has("morningNum"))
                scheduleConfig.morningNum = scheduleJson.optInt("morningNum");
            if (scheduleJson.has("afternoonNum"))
                scheduleConfig.afternoonNum = scheduleJson.optInt("afternoonNum");
            if (scheduleJson.has("nightNum"))
                scheduleConfig.nightNum = scheduleJson.optInt("nightNum");
            if (scheduleJson.has("sections")) {
                Object secObj = scheduleJson.opt("sections");
                if (secObj instanceof JSONArray) scheduleConfig.sections = secObj.toString();
                else scheduleConfig.sections = scheduleJson.optString("sections", null);
            }
            parseResult.schedule = scheduleConfig;
        }
        return parseResult;
    }

    public static void uploadCoursesAll(List<Course> courses, long ctId, String appId,
                                        String serviceToken, String deviceId) throws Exception {
        JSONArray courseArray = new JSONArray();
        for (Course course : courses) {
            String styleStr = course.style != null ? course.style : COLOR_PRESETS[0];
            JSONObject courseObj = new JSONObject()
                    .put("name", course.name)
                    .put("position", course.position)
                    .put("teacher", course.teacher)
                    .put("day", course.day)
                    .put("sections", course.sections)
                    .put("style", styleStr)
                    .put("weeks", course.weeks)
                    .put("extend", "");
            courseArray.put(courseObj);
        }

        JSONObject body = new JSONObject()
                .put("ctId", ctId)
                .put("courses", courseArray)
                .put("sourceName", SOURCE_NAME);

        String requestId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Request req = new Request.Builder()
                .url(BASE_URL + "/course-multi-auth/courseInfos")
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("RequestId", requestId)
                .header("Origin", BASE_URL)
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
                .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                .header("Access-Control-Allow-Origin", "true")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; 23113RKC6C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.14 Mobile Safari/537.36 MIAI/7.512.1.0917")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response resp = CLIENT.newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (resp.code() == 401) throw new UnauthorizedException("Token expired");
            if (!resp.isSuccessful()) throw new Exception(resp.code() + ": " + raw);
            JSONObject json = new JSONObject(raw);

            if (json.has("status") && json.optInt("status") == -1) {
                throw new Exception("批量创建失败: status=-1 (可能课程参数不合法)");
            }

            int code = json.optInt("code", 0);
            if (code != 0 && code != 200) {
                String desc = json.optString("desc", json.optString("msg", "未知错误"));
                throw new Exception(desc);
            }
        }
    }

    private static String buildAuth(String appId, String serviceToken, String deviceId) {
        // 如果是宿主已经构建好的完整 Authorization header，直接透传
        if (serviceToken != null && (
                serviceToken.startsWith("DO-TOKEN") ||
                serviceToken.startsWith("AO-TOKEN") ||
                serviceToken.startsWith("Bearer ") ||
                serviceToken.contains("app_id:") ||
                serviceToken.contains("access_token:"))) {
            return serviceToken;
        }
        try {
            String scopeJson = "{\"d\":\"" + deviceId + "\"}";
            String scopeData = Base64.encodeToString(scopeJson.getBytes("UTF-8"), Base64.NO_WRAP);
            String auth = "DO-TOKEN-V1 app_id:" + appId + ",scope_data:" + scopeData + ",access_token:" + serviceToken;
            return auth;
        } catch (Exception e) {
            return "";
        }
    }

    public static List<CourseTable> fetchTables(String appId, String serviceToken, String deviceId) throws Exception {
        String url = BASE_URL + "/course-multi-auth/tables?requestId=" + UUID.randomUUID().toString().replace("-", "").toUpperCase() + "&sourceName=" + SOURCE_NAME;
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; wv) AppleWebKit/537.36")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .get()
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                if (resp.code() == 401) throw new UnauthorizedException("Token expired");
                if (resp.code() == 500) {
                    throw new Exception("HTTP 500: 认证失效，请重新获取用户信息");
                }
                throw new Exception("请求课表列表失败(HTTP " + resp.code() + ")");
            }
            JSONObject json = new JSONObject(raw);
            if (json.optInt("code", -1) != 0)
                throw new Exception(json.optString("desc", "获取课表列表失败"));
            JSONArray data = json.getJSONArray("data");
            List<CourseTable> list = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.getJSONObject(i);
                CourseTable t = new CourseTable();
                t.id = o.getLong("id");
                t.name = o.optString("name", "未命名");
                // API 可能返回布尔值 true/false，也可能返回 int 1/0
                Object curObj = o.opt("current");
                if (curObj instanceof Boolean) {
                    t.current = ((Boolean) curObj) ? 1 : 0;
                } else {
                    t.current = o.optInt("current", 0);
                }
                if (o.has("setting")) t.settingStr = o.getJSONObject("setting").toString();
                list.add(t);
            }
            return list;
        }
    }

    public static void fetchTableDetail(CourseTable table, String appId, String serviceToken, String deviceId) throws Exception {
        String url = BASE_URL + "/course-multi-auth/table?ctId=" + table.id + "&requestId=" + UUID.randomUUID().toString().replace("-", "").toUpperCase() + "&sourceName=" + SOURCE_NAME;
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; wv) AppleWebKit/537.36")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .get()
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful())
                throw new Exception("查询课表详情失败(HTTP " + resp.code() + ")");
            JSONObject json = new JSONObject(raw);
            if (json.optInt("code", -1) != 0)
                throw new Exception(json.optString("desc", "查询课表详情失败"));
            JSONObject data = json.getJSONObject("data");
            if (data.has("setting")) table.settingStr = data.getJSONObject("setting").toString();
            table.existingCourseIds.clear();
            if (data.has("courses")) {
                JSONArray arr = data.getJSONArray("courses");
                for (int i = 0; i < arr.length(); i++) {
                    table.existingCourseIds.add(arr.getJSONObject(i).getLong("id"));
                }
            }
        }
    }

    public static void deleteCourse(long ctId, long cId, String appId, String serviceToken, String deviceId) throws Exception {
        JSONObject body = new JSONObject().put("ctId", ctId).put("cId", cId).put("sourceName", SOURCE_NAME);
        Request req = new Request.Builder()
                .url(BASE_URL + "/course-multi-auth/courseInfo")
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .delete(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new Exception("删除旧课表失败(HTTP " + resp.code() + ")");
        }
    }

    public static long createTable(String name, String appId, String serviceToken, String deviceId) throws Exception {
        JSONObject body = new JSONObject().put("name", name).put("current", 0).put("sourceName", SOURCE_NAME);
        Request req = new Request.Builder()
                .url(BASE_URL + "/course-multi-auth/table")
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .header("sec-ch-ua", SEC_CH_UA)
                .header("sec-ch-ua-mobile", SEC_CH_UA_MOBILE)
                .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
                .header("Access-Control-Allow-Origin", "true")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; 23113RKC6C) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.14 Mobile Safari/537.36 MIAI/7.512.1.0917")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                if (resp.code() == 401) throw new UnauthorizedException("Token expired");
                if (resp.code() == 500) {
                    throw new Exception("HTTP 500: 认证失效或服务端异常，请检查登录状态");
                }
                throw new Exception("新建课表请求失败(HTTP " + resp.code() + ")");
            }
            JSONObject json = new JSONObject(raw);
            if (json.optInt("code", -1) != 0) {
                String desc = json.optString("desc", "新建课表响应错误");
                // "repeat request" 表示服务端认为短时间内重复建表，尝试找到同名表复用
                if (desc.toLowerCase().contains("repeat")) {
                    android.util.Log.w("ApiClient", "createTable repeat detected, recovering existing table: " + name);
                    List<CourseTable> existing = fetchTables(appId, serviceToken, deviceId);
                    for (CourseTable t : existing) {
                        if (name.equals(t.name)) {
                            android.util.Log.i("ApiClient", "Reusing existing table id=" + t.id);
                            return t.id;
                        }
                    }
                }
                throw new Exception(desc);
            }
            return json.getLong("data");
        }
    }

    public static void updateTableSettings(long ctId, String name, String sourceSettingStr, String originalSettingStr, ScheduleConfig customSchedule, boolean preferSourceTermFields, String appId, String serviceToken, String deviceId) throws Exception {
        // 两者均有值时才执行；否则没有足够数据构建合法 setting
        if (originalSettingStr == null || originalSettingStr.isEmpty())
            return;

        JSONObject sourceRoot = (sourceSettingStr == null || sourceSettingStr.isEmpty())
                ? new JSONObject()
                : new JSONObject(sourceSettingStr);
        JSONObject sourceObj = sourceRoot.optJSONObject("courseConfig");
        if (sourceObj == null) sourceObj = sourceRoot;
        JSONObject origObj = new JSONObject(originalSettingStr);
        // 从零构建 merged，仅放入明确需要的字段
        JSONObject merged = new JSONObject(origObj.toString());
        if (!sourceObj.has("startSemester") && sourceObj.has("semesterStartDate"))
            sourceObj.put("startSemester", sourceObj.opt("semesterStartDate"));
        if (!sourceObj.has("totalWeek") && sourceObj.has("semesterTotalWeeks"))
            sourceObj.put("totalWeek", sourceObj.opt("semesterTotalWeeks"));
        if (!sourceObj.has("weekStart") && sourceObj.has("firstDayOfWeek"))
            sourceObj.put("weekStart", sourceObj.opt("firstDayOfWeek"));
        if (!sourceObj.has("startSemester") && sourceObj.has("startDate"))
            sourceObj.put("startSemester", sourceObj.opt("startDate"));
        if (!sourceObj.has("startSemester") && sourceObj.has("termStartDate"))
            sourceObj.put("startSemester", sourceObj.opt("termStartDate"));

        // 固定字段：从新建课表默认值取（保证 id/学期等为本次课表的正确值）
        if (origObj.has("id")) merged.put("id", origObj.get("id"));
        if (origObj.has("presentWeek")) merged.put("presentWeek", origObj.get("presentWeek"));
        if (origObj.has("totalWeek")) merged.put("totalWeek", origObj.get("totalWeek"));
        if (origObj.has("startSemester")) merged.put("startSemester", origObj.get("startSemester"));

        // 用户习惯字段：从导入前活跃课表取（保留用户的周末/语音等偏好）
        String[] cloneKeys = {"isWeekend", "morningNum", "afternoonNum", "nightNum", "speak", "weekStart"};
        for (String k : cloneKeys) {
            if (!sourceObj.has(k)) continue;
            Object v = sourceObj.get(k);
            if (v != null && !(v instanceof String && ((String) v).trim().isEmpty())) {
                merged.put(k, v);
            }
        }
        if (preferSourceTermFields) {
            if (sourceObj.has("startSemester")) {
                String normalizedStartSemester = normalizeStartSemester(sourceObj.opt("startSemester"));
                if (normalizedStartSemester != null && !normalizedStartSemester.isEmpty()) {
                    merged.put("startSemester", normalizedStartSemester);
                }
            }
            if (sourceObj.has("totalWeek")) {
                Object totalWeekVal = sourceObj.opt("totalWeek");
                if (totalWeekVal != null && !(totalWeekVal instanceof String && ((String) totalWeekVal).trim().isEmpty())) {
                    merged.put("totalWeek", totalWeekVal);
                }
            }
            if (sourceObj.has("presentWeek")) {
                Object presentWeekVal = sourceObj.opt("presentWeek");
                if (presentWeekVal != null && !(presentWeekVal instanceof String && ((String) presentWeekVal).trim().isEmpty())) {
                    merged.put("presentWeek", presentWeekVal);
                }
            }
        }

        // 时间节次-sections：优先用解析结果，否则取活跃课表值；始终序列化为 JSON 字符串
        if (sourceObj.has("sections")) {
            Object secObj = sourceObj.get("sections");
            merged.put("sections", secObj instanceof String ? secObj : secObj.toString());
        } else if (sourceObj.has("sectionTimes")) {
            Object stObj = sourceObj.get("sectionTimes");
            merged.put("sections", stObj instanceof String ? stObj : stObj.toString());
        }
        if (customSchedule != null) {
            if (customSchedule.morningNum != null) merged.put("morningNum", (int) customSchedule.morningNum);
            if (customSchedule.afternoonNum != null) merged.put("afternoonNum", (int) customSchedule.afternoonNum);
            if (customSchedule.nightNum != null) merged.put("nightNum", (int) customSchedule.nightNum);
            if (customSchedule.sections != null && !customSchedule.sections.isEmpty())
                merged.put("sections", customSchedule.sections);
        }

        // school 必须为 JSON 字符串（服务端要求）
        String school = merged.optString("school", "{}");
        if (sourceObj.has("school")) {
            Object srcSchool = sourceObj.opt("school");
            school = srcSchool instanceof String ? (String) srcSchool : (srcSchool != null ? srcSchool.toString() : "{}");
        }
        if (school.isEmpty()) school = "{}";
        merged.put("school", school);

        // extend：以新建课表的 extend 为基础，覆盖活跃课表的 bgSetting/degree/showNotInWeek
        JSONObject mergedExt = new JSONObject();
        try {
            Object origExtVal = origObj.opt("extend");
            String origExtStr = origExtVal instanceof String ? (String) origExtVal : (origExtVal != null ? origExtVal.toString() : "{}");
            mergedExt = new JSONObject(origExtStr.isEmpty() ? "{}" : origExtStr);
        } catch (Exception ignored) {}
        try {
            Object srcExtVal = sourceObj.opt("extend");
            String srcExtStr = srcExtVal instanceof String ? (String) srcExtVal : (srcExtVal != null ? srcExtVal.toString() : "{}");
            JSONObject sourceExt = new JSONObject(srcExtStr.isEmpty() ? "{}" : srcExtStr);
            if (sourceExt.has("bgSetting")) mergedExt.put("bgSetting", sourceExt.get("bgSetting"));
            if (sourceExt.has("degree")) mergedExt.put("degree", sourceExt.get("degree"));
            if (sourceExt.has("showNotInWeek")) mergedExt.put("showNotInWeek", sourceExt.get("showNotInWeek"));
            if (sourceExt.has("startSemester")) mergedExt.put("startSemester", sourceExt.get("startSemester"));
        } catch (Exception ignored) {}
        String finalStartSemester = merged.optString("startSemester", "");
        if (!finalStartSemester.isEmpty()) {
            try {
                mergedExt.put("startSemester", Long.parseLong(finalStartSemester));
            } catch (Exception ignored) {
                mergedExt.put("startSemester", finalStartSemester);
            }
        }
        merged.put("extend", mergedExt.toString());

        JSONObject body = new JSONObject()
                .put("ctId", ctId)
                .put("name", name)
                .put("setting", merged)
                .put("sourceName", SOURCE_NAME);

        // 旧版 API 使用 PUT 方法，URL 无额外查询参数
        Request req = new Request.Builder()
                .url(BASE_URL + "/course-multi-auth/table")
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", X_REQUESTED_WITH)
                .header("Referer", BASE_URL + "/h5/precache/ai-schedule/")
                .put(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response resp = CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                if (resp.code() == 401) throw new UnauthorizedException("Token expired");
                // 设置项上传非关键，不阻断整体导入流程
                android.util.Log.w("ApiClient", "updateTableSettings failed HTTP " + resp.code() + ", ignored");
            }
        }
    }

    public static void switchTable(long fromCtId, long toCtId, String appId, String serviceToken, String deviceId) throws Exception {
        JSONObject body = new JSONObject()
                .put("fromCtId", fromCtId)
                .put("toCtId", toCtId)
                .put("sourceName", "course-app-miui");
        Request req = new Request.Builder()
                .url("https://i.xiaomixiaoai.com/course-multi-auth/table_switch")
                .header("Authorization", buildAuth(appId, serviceToken, deviceId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16; wv) AppleWebKit/537.36 Mobile Safari/537.36")
                .header("Origin", "https://i.xiaomixiaoai.com")
                .header("X-Requested-With", "com.miui.voiceassist")
                .header("Referer", "https://i.xiaomixiaoai.com/h5/precache/ai-schedule/")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            String raw = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                if (resp.code() == 401) throw new UnauthorizedException("Token expired");
                throw new Exception("切换课表失败(HTTP " + resp.code() + "): " + raw);
            }
            JSONObject json = new JSONObject(raw);
            int code = json.optInt("code", -1);
            if (code != 0 && code != 200)
                throw new Exception(json.optString("desc", json.optString("msg", "切换课表失败")));
        }
    }

    public static String translateJsonError(String msg) {
        if (msg == null) return "未知语法错误";
        if (msg.contains("Expected a ':' after a key")) return "键值对缺少冒号";
        if (msg.contains("Expected a ',' or '}'")) return "缺少逗号或右大括号(部分结构不闭合)";
        if (msg.contains("Expected a ',' or ']'")) return "缺少逗号或右中括号(部分结构不闭合)";
        if (msg.contains("Unterminated string")) return "字符串缺少闭合的双引号";
        if (msg.contains("Unterminated object")) return "对象缺少闭合的大括号";
        if (msg.contains("Unterminated array")) return "数组缺少闭合的中括号";
        if (msg.contains("Value out of range")) return "数值超出允许范围";
        if (msg.contains("Expected literal value")) return "存在多余符号或非法字符";
        if (msg.contains("End of input")) return "由于长度超出等原因导致 JSON 被意外阶段而残缺";
        return msg;
    }

    public static String repairAiJson(String json) {
        if (json == null || json.isEmpty()) return json;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"\\s*(\\[\\s*\\{.*?\\}\\s*\\])\\s*\"", java.util.regex.Pattern.DOTALL).matcher(json);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String innerArray = m.group(2).replace("\\\"", "\"");
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("\"" + key + "\": " + innerArray));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String normalizeStartSemester(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        if (s.matches("^\\d{10,13}$")) {
            if (s.length() == 10) return s + "000";
            return s;
        }
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy.M.d", "yyyy-M-d"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                long ts = sdf.parse(s).getTime();
                return String.valueOf(ts);
            } catch (Exception ignored) {}
        }
        return s;
    }

    public static void fetchUpdateHtml(String url, Callback callback) {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36")
                .build();
        CLIENT.newCall(req).enqueue(callback);
    }
    public static class UnauthorizedException extends Exception {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
