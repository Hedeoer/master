package com.zeta.firewall.model.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 使用redis stream向某个streamKey发布命令时的数据格式映射类
 * @param <T>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisCommandMessage<T> {
    @JsonProperty("agent_id")
    private String agentId;
    
    @JsonProperty("agent_component_type")
    private ComponentType agentComponentType;
    
    @JsonProperty("data_op_type")
    private OperationType dataOpType;
    
    @JsonProperty("request_params")
    private Map<String, String> requestParams;
    
    private Long ts;
    
    @JsonProperty("primary_key_columns")
    private List<String> primaryKeyColumns;
    
    @JsonProperty("data")
    private List<T> data;
    
    @JsonProperty("old")
    private T old;
    
    // 操作类型枚举
    public enum OperationType {
        @JsonEnumDefaultValue
        UNKNOWN,
        QUERY,
        INSERT,
        DELETE,
        UPDATE
    }
    
    // 组件类型枚举
    public enum ComponentType {
        @JsonEnumDefaultValue
        UNKNOWN,
        FIREWALL
        // 其他组件类型...
    }
}