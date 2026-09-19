package com.example.scada.dictionary;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController {
    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping
    public List<DictionaryTypeResponse> listTypes() {
        return dictionaryService.listTypes();
    }

    @GetMapping("/{typeCode}/items")
    public List<DictionaryItemResponse> listItems(@PathVariable String typeCode) {
        return dictionaryService.listItems(typeCode);
    }

    @GetMapping("/items")
    public Map<String, List<DictionaryItemResponse>> listItemsByTypes(@RequestParam List<String> typeCodes) {
        return dictionaryService.listItemsByTypes(typeCodes);
    }
}
