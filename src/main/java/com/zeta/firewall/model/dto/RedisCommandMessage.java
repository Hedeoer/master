package com.zeta.firewall.model.dto;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.zeta.firewall.util.ComponentTypeDeserializer;
import com.zeta.firewall.util.OperationTypeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
public class RedisCommandMessage<T> {
    
    private String agentId;
    
    @JsonDeserialize(using = ComponentTypeDeserializer.class)
    private ComponentType agentComponentType;

    @JsonDeserialize(using = OperationTypeDeserializer.class)
    private OperationType dataOpType;

    private Map<String, String> requestParams;

    private Long ts;

    private List<String> primaryKeyColumns;

    private List<T> data;

    private T old;

    // 操作类型枚举
    public enum OperationType {
        @JsonEnumDefaultValue
        UNKNOWN,
        QUERY,
        INSERT,
        DELETE,
        UPDATE,
        OPTIONS
    }

    // 组件类型枚举
    public enum ComponentType {
        @JsonEnumDefaultValue
        UNKNOWN,
        PORT,
        FIREWALL
        // 其他组件类型...
    }


}