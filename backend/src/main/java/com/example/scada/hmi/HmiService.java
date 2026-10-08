package com.example.scada.hmi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.scada.auth.AuthUser;
import com.example.scada.auth.CurrentUserHolder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class HmiService {
    private static final int MAX_DOCUMENT_BYTES = 1_000_000;
    private static final Pattern ITEM_ID = Pattern.compile("[a-zA-Z0-9-]+");
    private static final Set<String> KINDS = Set.of("value", "lamp", "button", "text", "rectangle", "ellipse", "line", "pipe");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public HmiService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public HmiConfigResponse getDraft(Long configId) {
        requireScreen(configId);
        return jdbcTemplate.queryForObject("""
                select c.*, r.version_no published_version
                from scada_hmi_config c
                left join scada_hmi_revision r on r.id = c.published_revision_id
                where c.id = ?
                """, (rs, rowNum) -> mapConfig(rs), configId);
    }

    @Transactional
    public HmiConfigResponse saveDraft(Long configId, HmiDocumentRequest request) {
        requireScreen(configId);
        if (request == null) throw new IllegalArgumentException("组态文档不能为空");
        String json = validateAndSerialize(request.document());
        Long expected = request.expectedDraftVersion();
        int updated = expected == null
                ? jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ?", json, actor(), configId)
                : jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ? and draft_version = ?", json, actor(), configId, expected);
        if (updated == 0) throw new IllegalArgumentException("草稿已被其他会话修改，请刷新后再保存");
        audit("HMI_DRAFT_SAVE", "保存组态草稿");
        return getDraft(configId);
    }

    @Transactional
    public HmiRevisionResponse publish(Long configId, HmiDocumentRequest request) {
        requireScreen(configId);
        jdbcTemplate.queryForObject("select id from scada_hmi_config where id = ? for update", Long.class, configId);
        HmiConfigResponse saved = saveDraft(configId, request);
        Integer next = jdbcTemplate.queryForObject("select coalesce(max(version_no), 0) + 1 from scada_hmi_revision where config_id = ?", Integer.class, configId);
        int version = next == null ? 1 : next;
        jdbcTemplate.update("insert into scada_hmi_revision(config_id, version_no, content_json, published_by) values (?, ?, ?, ?)", configId, version, serialize(saved.document()), actor());
        Long revisionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("update scada_hmi_config set published_revision_id = ? where id = ?", revisionId, configId);
        audit("HMI_PUBLISH", "发布组态版本 V" + version);
        return getRevision(configId, revisionId);
    }

    public HmiRevisionResponse getPublished(Long configId) {
        requireScreen(configId);
        Long id = jdbcTemplate.queryForObject("select published_revision_id from scada_hmi_config where id = ?", Long.class, configId);
        if (id == null) throw new IllegalArgumentException("尚未发布组态版本");
        return getRevision(configId, id);
    }

    public List<HmiRevisionResponse> listVersions(Long configId) {
        requireScreen(configId);
        return jdbcTemplate.query("""
                select r.*, (r.id = c.published_revision_id) current_revision
                from scada_hmi_revision r join scada_hmi_config c on c.id = r.config_id
                where r.config_id = ? order by r.version_no desc limit 50
                """, (rs, rowNum) -> mapRevision(rs), configId);
    }

    @Transactional
    public HmiConfigResponse restore(Long configId, Long id) {
        requireScreen(configId);
        HmiRevisionResponse revision = getRevision(configId, id);
        jdbcTemplate.update("update scada_hmi_config set draft_json = ?, draft_version = draft_version + 1, updated_by = ? where id = ?", serialize(revision.document()), actor(), configId);
        audit("HMI_RESTORE", "恢复组态版本 V" + revision.version() + " 到草稿");
        return getDraft(configId);
    }

    private HmiRevisionResponse getRevision(Long configId, Long id) {
        List<HmiRevisionResponse> rows = jdbcTemplate.query("""
                select r.*, (r.id = c.published_revision_id) current_revision
                from scada_hmi_revision r join scada_hmi_config c on c.id = r.config_id
                where r.id = ? and r.config_id = ?
                """, (rs, rowNum) -> mapRevision(rs), id, configId);
        if (rows.isEmpty()) throw new IllegalArgumentException("组态版本不存在");
        return rows.getFirst();
    }

    public List<HmiScreenResponse> listScreens() {
        return jdbcTemplate.query("""
                select c.*, r.version_no published_version from scada_hmi_config c
                left join scada_hmi_revision r on r.id = c.published_revision_id
                order by c.sort_order, c.id
                """, (rs, rowNum) -> mapScreen(rs));
    }

    @Transactional
    public HmiScreenResponse createScreen(HmiScreenRequest request) {
        ScreenFields fields = validateScreen(request);
        try {
            jdbcTemplate.update("""
                    insert into scada_hmi_config(screen_code, screen_name, enabled, sort_order, draft_json, updated_by)
                    values (?, ?, ?, (select coalesce(max_order, 0) + 10 from (select max(sort_order) max_order from scada_hmi_config) current_order), '{"version":1,"items":[]}', ?)
                    """, fields.code(), fields.name(), fields.enabled() ? 1 : 0, actor());
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("画面编码已存在"); }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        audit("HMI_SCREEN_CREATE", "创建组态画面 " + fields.code());
        return getScreen(id);
    }

    @Transactional
    public HmiScreenResponse updateScreen(Long id, HmiScreenRequest request) {
        requireScreen(id);
        ScreenFields fields = validateScreen(request);
        Boolean currentDefault = jdbcTemplate.queryForObject("select is_default from scada_hmi_config where id = ?", Boolean.class, id);
        if (Boolean.TRUE.equals(currentDefault) && !fields.enabled()) throw new IllegalArgumentException("默认运行画面不能停用，请先设置其他默认画面");
        try {
            jdbcTemplate.update("update scada_hmi_config set screen_code = ?, screen_name = ?, enabled = ?, updated_by = ? where id = ?",
                    fields.code(), fields.name(), fields.enabled() ? 1 : 0, actor(), id);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("画面编码已存在"); }
        audit("HMI_SCREEN_UPDATE", "更新组态画面 " + fields.code());
        return getScreen(id);
    }

    @Transactional
    public HmiScreenResponse copyScreen(Long sourceId, HmiScreenRequest request) {
        requireScreen(sourceId);
        ScreenFields fields = validateScreen(request);
        String document = jdbcTemplate.queryForObject("select draft_json from scada_hmi_config where id = ?", String.class, sourceId);
        try {
            jdbcTemplate.update("""
                    insert into scada_hmi_config(screen_code, screen_name, enabled, sort_order, draft_json, updated_by)
                    values (?, ?, ?, (select coalesce(max_order, 0) + 10 from (select max(sort_order) max_order from scada_hmi_config) current_order), ?, ?)
                    """, fields.code(), fields.name(), fields.enabled() ? 1 : 0, document, actor());
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("画面编码已存在"); }
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        audit("HMI_SCREEN_COPY", "复制组态画面到 " + fields.code());
        return getScreen(id);
    }

    @Transactional
    public HmiScreenResponse setDefault(Long id) {
        requireScreen(id);
        Boolean enabled = jdbcTemplate.queryForObject("select enabled from scada_hmi_config where id = ?", Boolean.class, id);
        if (!Boolean.TRUE.equals(enabled)) throw new IllegalArgumentException("停用画面不能设为默认运行画面");
        jdbcTemplate.update("update scada_hmi_config set is_default = 0 where is_default = 1");
        jdbcTemplate.update("update scada_hmi_config set is_default = 1, updated_by = ? where id = ?", actor(), id);
        audit("HMI_SCREEN_DEFAULT", "设置默认组态画面 " + id);
        return getScreen(id);
    }

    @Transactional
    public void moveScreen(Long id, String direction) {
        requireScreen(id);
        String normalized = direction == null ? "" : direction.trim().toUpperCase();
        if (!Set.of("UP", "DOWN").contains(normalized)) throw new IllegalArgumentException("画面排序方向无效");
        Integer current = jdbcTemplate.queryForObject("select sort_order from scada_hmi_config where id = ? for update", Integer.class, id);
        String operator = "UP".equals(normalized) ? "<" : ">";
        String order = "UP".equals(normalized) ? "desc" : "asc";
        List<long[]> neighbor = jdbcTemplate.query("select id, sort_order from scada_hmi_config where sort_order " + operator + " ? order by sort_order " + order + ", id " + order + " limit 1 for update",
                (rs, rowNum) -> new long[] { rs.getLong("id"), rs.getInt("sort_order") }, current);
        if (neighbor.isEmpty()) return;
        long[] target = neighbor.getFirst();
        jdbcTemplate.update("update scada_hmi_config set sort_order = ? where id = ?", target[1], id);
        jdbcTemplate.update("update scada_hmi_config set sort_order = ? where id = ?", current, target[0]);
        audit("HMI_SCREEN_MOVE", "调整组态画面顺序 " + id + " " + normalized);
    }

    @Transactional
    public void deleteScreen(Long id) {
        requireScreen(id);
        HmiScreenResponse screen = getScreen(id);
        if (screen.defaultScreen()) throw new IllegalArgumentException("默认运行画面不能删除，请先设置其他默认画面");
        Integer count = jdbcTemplate.queryForObject("select count(*) from scada_hmi_config", Integer.class);
        if (count == null || count <= 1) throw new IllegalArgumentException("系统至少保留一个组态画面");
        jdbcTemplate.update("delete from scada_hmi_revision where config_id = ?", id);
        jdbcTemplate.update("delete from scada_hmi_config where id = ?", id);
        audit("HMI_SCREEN_DELETE", "删除组态画面 " + screen.code());
    }

    private HmiScreenResponse getScreen(Long id) {
        return jdbcTemplate.queryForObject("""
                select c.*, r.version_no published_version from scada_hmi_config c
                left join scada_hmi_revision r on r.id = c.published_revision_id where c.id = ?
                """, (rs, rowNum) -> mapScreen(rs), id);
    }

    private HmiScreenResponse mapScreen(ResultSet rs) throws SQLException {
        return new HmiScreenResponse(rs.getLong("id"), rs.getString("screen_code"), rs.getString("screen_name"), rs.getBoolean("enabled"), rs.getInt("sort_order"), rs.getBoolean("is_default"),
                rs.getLong("draft_version"), nullableLong(rs, "published_revision_id"), nullableInt(rs, "published_version"),
                rs.getString("updated_by"), rs.getTimestamp("updated_at").toInstant());
    }

    private ScreenFields validateScreen(HmiScreenRequest request) {
        if (request == null) throw new IllegalArgumentException("画面信息不能为空");
        String code = request.code() == null ? "" : request.code().trim().toUpperCase();
        String name = request.name() == null ? "" : request.name().trim();
        if (!code.matches("[A-Z0-9_-]{2,64}")) throw new IllegalArgumentException("画面编码仅支持 2–64 位大写字母、数字、下划线和短横线");
        if (name.isEmpty() || name.length() > 128) throw new IllegalArgumentException("画面名称长度应为 1–128 个字符");
        return new ScreenFields(code, name, !Boolean.FALSE.equals(request.enabled()));
    }

    private void requireScreen(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("组态画面不存在");
        Integer count = jdbcTemplate.queryForObject("select count(*) from scada_hmi_config where id = ?", Integer.class, id);
        if (count == null || count == 0) throw new IllegalArgumentException("组态画面不存在");
    }

    private record ScreenFields(String code, String name, boolean enabled) { }

    private HmiConfigResponse mapConfig(ResultSet rs) throws SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new HmiConfigResponse(parse(rs.getString("draft_json")), rs.getLong("draft_version"),
                nullableLong(rs, "published_revision_id"), nullableInt(rs, "published_version"), rs.getString("updated_by"), updatedAt.toInstant());
    }

    private HmiRevisionResponse mapRevision(ResultSet rs) throws SQLException {
        return new HmiRevisionResponse(rs.getLong("id"), rs.getInt("version_no"), parse(rs.getString("content_json")),
                rs.getString("published_by"), rs.getTimestamp("created_at").toInstant(), rs.getBoolean("current_revision"));
    }

    private String validateAndSerialize(JsonNode document) {
        if (document == null || !document.isObject() || document.path("version").asInt(-1) != 1 || !document.path("items").isArray()) {
            throw new IllegalArgumentException("组态文档格式无效");
        }
        JsonNode items = document.path("items");
        if (items.size() > 1000) throw new IllegalArgumentException("组态组件不能超过 1000 个");
        for (JsonNode item : items) validateItem(item);
        String json = serialize(document);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("组态文档不能超过 1 MB");
        return json;
    }

    private void validateItem(JsonNode item) {
        String id = item.path("id").asText("");
        String kind = item.path("kind").asText("");
        String label = item.path("label").asText("");
        double x = number(item, "x"), y = number(item, "y"), width = number(item, "width"), height = number(item, "height");
        if (!ITEM_ID.matcher(id).matches() || !KINDS.contains(kind) || label.length() > 80 || width < 40 || height < 40 || x < 0 || y < 0 || x + width > 1200 || y + height > 720) {
            throw new IllegalArgumentException("组态组件格式无效");
        }
        JsonNode groupId = item.get("groupId");
        if (groupId != null && (!groupId.isTextual() || !ITEM_ID.matcher(groupId.asText()).matches())) throw new IllegalArgumentException("组态组件分组格式无效");
        JsonNode locked = item.get("locked");
        if (locked != null && !locked.isBoolean()) throw new IllegalArgumentException("组态组件锁定格式无效");
        JsonNode binding = item.get("binding");
        if (binding != null && (!binding.isObject() || binding.path("deviceId").asLong(0) <= 0 ||
                (!binding.path("pointId").isNull() && binding.path("pointId").asLong(0) <= 0) ||
                binding.path("decimals").asInt(-1) < 0 || binding.path("decimals").asInt(7) > 6 ||
                binding.path("staleSeconds").asInt(0) < 5 || binding.path("staleSeconds").asInt(3601) > 3600)) {
            throw new IllegalArgumentException("组态点位绑定格式无效");
        }
    }

    private double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalArgumentException("组态组件坐标无效");
        return value.asDouble();
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json); }
        catch (JacksonException ex) { throw new IllegalStateException("数据库中的组态文档损坏", ex); }
    }

    private String serialize(JsonNode document) {
        try { return objectMapper.writeValueAsString(document); }
        catch (JacksonException ex) { throw new IllegalArgumentException("组态文档无法序列化", ex); }
    }

    private Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }

    private String actor() {
        AuthUser user = CurrentUserHolder.user();
        return user == null ? "system" : user.username();
    }

    private void audit(String action, String detail) {
        jdbcTemplate.update("insert into sys_audit_log(actor, action, detail) values (?, ?, ?)", actor(), action, detail);
    }
}
