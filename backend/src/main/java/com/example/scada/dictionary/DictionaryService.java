package com.example.scada.dictionary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DictionaryService {
    private final JdbcTemplate jdbcTemplate;

    public DictionaryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DictionaryTypeResponse> listTypes() {
        return jdbcTemplate.query("""
                select type_code, name, description
                from sys_dict_type
                where enabled = 1
                order by sort_order, id
                """, (rs, rowNum) -> new DictionaryTypeResponse(
                rs.getString("type_code"),
                rs.getString("name"),
                rs.getString("description")
        ));
    }

    public List<DictionaryItemResponse> listItems(String typeCode) {
        return jdbcTemplate.query("""
                select t.type_code, i.item_code, i.label, i.description, i.sort_order
                from sys_dict_item i
                join sys_dict_type t on t.id = i.type_id
                where t.type_code = ? and t.enabled = 1 and i.enabled = 1
                order by i.sort_order, i.id
                """, (rs, rowNum) -> mapItem(rs), typeCode);
    }

    public Map<String, List<DictionaryItemResponse>> listItemsByTypes(List<String> typeCodes) {
        return typeCodes.stream().collect(Collectors.toMap(typeCode -> typeCode, this::listItems));
    }

    private DictionaryItemResponse mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DictionaryItemResponse(
                rs.getString("type_code"),
                rs.getString("item_code"),
                rs.getString("label"),
                rs.getString("description"),
                rs.getInt("sort_order")
        );
    }
}
