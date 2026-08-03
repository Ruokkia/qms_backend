package com.kangli.qms.service.spc.impl;

import com.kangli.qms.common.SpcItemDictDTO;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.service.spc.SpcItemDictService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M4 SPC 统一代码字典 Service 实现。
 * <p>将“已签首件”与“已激活标准”合并，按 (itemType, itemCode) 去重；
 * 同名项合并 processCode（取首个非空）与 paramCode（拼接去重）。</p>
 */
@Slf4j
@Service
public class SpcItemDictServiceImpl implements SpcItemDictService {

    private final FaiInspectionRecordMapper faiRecordMapper;
    private final FaiInspectionStandardMapper standardMapper;

    public SpcItemDictServiceImpl(FaiInspectionRecordMapper faiRecordMapper,
                                  FaiInspectionStandardMapper standardMapper) {
        this.faiRecordMapper = faiRecordMapper;
        this.standardMapper = standardMapper;
    }

    @Override
    public List<SpcItemDictDTO> search(String plantCode, String keyword) {
        List<SpcItemDictDTO> signed = faiRecordMapper.selectSignedDict(plantCode);
        List<SpcItemDictDTO> active = standardMapper.selectActiveStandardDict(plantCode);

        // 按 (itemType, itemCode) 去重聚合
        Map<String, SpcItemDictDTO> map = new LinkedHashMap<>();
        mergeInto(map, signed);
        mergeInto(map, active);

        List<SpcItemDictDTO> all = new ArrayList<>(map.values());

        if (keyword == null || keyword.trim().isEmpty()) {
            return all;
        }
        String kw = keyword.trim().toLowerCase();
        List<SpcItemDictDTO> filtered = new ArrayList<>();
        for (SpcItemDictDTO d : all) {
            String code = d.getItemCode() == null ? "" : d.getItemCode().toLowerCase();
            String name = d.getItemName() == null ? "" : d.getItemName().toLowerCase();
            if (code.contains(kw) || name.contains(kw)) {
                filtered.add(d);
            }
        }
        return filtered;
    }

    private void mergeInto(Map<String, SpcItemDictDTO> map, List<SpcItemDictDTO> list) {
        if (list == null) {
            return;
        }
        for (SpcItemDictDTO d : list) {
            if (d.getItemCode() == null || d.getItemCode().isEmpty()) {
                continue;
            }
            String key = (d.getItemType() == null ? "" : d.getItemType()) + "|" + d.getItemCode();
            SpcItemDictDTO exist = map.get(key);
            if (exist == null) {
                map.put(key, d);
            } else {
                if ((exist.getProcessCode() == null || exist.getProcessCode().isEmpty())
                        && d.getProcessCode() != null) {
                    exist.setProcessCode(d.getProcessCode());
                }
                exist.setParamCode(mergeParamCodes(exist.getParamCode(), d.getParamCode()));
            }
        }
    }

    private String mergeParamCodes(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        Map<String, String> set = new LinkedHashMap<>();
        for (String s : (a + "," + b).split(",")) {
            if (s != null && !s.trim().isEmpty()) {
                set.put(s.trim(), s.trim());
            }
        }
        return String.join(",", set.keySet());
    }
}
