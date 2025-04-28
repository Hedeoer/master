package com.zeta.firewall.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.zeta.firewall.model.dto.AgentNodeInfoDTO;
import com.zeta.firewall.model.dto.RedisCommandMessage;
import com.zeta.firewall.model.entity.AgentNodeInfo;
import com.zeta.firewall.model.entity.PortRule;
import com.zeta.firewall.model.param.AgentNodeQueryParam;
import com.zeta.firewall.schedule.HeartBeatService;
import com.zeta.firewall.service.AgentNodeInfoService;
import com.zeta.firewall.service.StreamResponseService;
import com.zeta.firewall.subscirbe.StreamProducer;
import com.zeta.firewall.util.JsonMessageConverter;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.web.bind.annotation.*;
import org.zetaframework.base.controller.SuperSimpleController;
import org.zetaframework.base.controller.extra.NoPageQueryController;
import org.zetaframework.base.param.PageParam;
import org.zetaframework.base.result.ApiResult;
import org.zetaframework.base.result.PageResult;
import org.zetaframework.core.log.annotation.SysLog;
import org.zetaframework.core.saToken.annotation.PreCheckPermission;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Agent节点 前端控制器
 *
 * 负责Agent节点相关的API接口，提供节点信息的查询功能
 *
 * @author AutoGenerator
 */

@Slf4j
@RequiredArgsConstructor
@Api(tags = "Agent节点管理")
//@PreAuth(replace = "agents:node") // 权限注解，可根据需要启用
@RestController
@RequestMapping("/api/agents/node")
public class AgentNodeController extends SuperSimpleController<AgentNodeInfoService, AgentNodeInfo>
        implements NoPageQueryController<AgentNodeInfo, String, AgentNodeQueryParam> {

    private final HeartBeatService heartBeatService;
    private final StreamProducer streamProducer;
    @Autowired
    private StreamResponseService streamResponseService;
    @Resource(name = "taskExecutor")  // 按名称注入，不需要 @Autowired
    private Executor taskExecutor;

    /**
     * 分页查询节点列表
     *
     * 根据查询参数分页获取Agent节点信息，并转换为前端所需的DTO格式
     *
     * @param param 分页查询参数
     * @return ApiResult<PageResult<AgentNodeInfoDTO>> 分页结果
     */
//    @PreCheckPermission(value = "{}:view") // 用于检查当前用户是否有查看权限
    @ApiOperationSupport(order = 10, author = "AutoGenerator")
    @ApiOperation(value = "分页查询节点列表") // Swagger文档注解
    @SysLog(response = false) // 系统日志记录注解
    @PostMapping("/page")
    public ApiResult<PageResult<AgentNodeInfoDTO>> page(@RequestBody PageParam<AgentNodeQueryParam> param) {
        // 设置默认排序字段
        if (param.getSort().equals("id")) {
            param.setSort("agent_id");
        }

        // 构造分页对象
        IPage<AgentNodeInfo> page = param.buildPage();

        // 构造查询条件
        AgentNodeQueryParam model = param.getModel();
        LambdaQueryWrapper<AgentNodeInfo> queryWrapper = new LambdaQueryWrapper<>();

        // 根据查询参数构建查询条件
        if (model != null) {
            // 根据节点ID查询
            if (model.getAgentId() != null) {
                queryWrapper.eq(AgentNodeInfo::getAgentId, model.getAgentId());
            }

            // 根据主机名查询 - 使用模糊匹配
            if (model.getHostName() != null) {
                queryWrapper.like(AgentNodeInfo::getHostName, model.getHostName());
            }

            // 根据IP查询 - 使用模糊匹配
            if (model.getIp() != null) {
                queryWrapper.like(AgentNodeInfo::getIp, model.getIp());
            }

            // 根据节点状态查询 - 在线/离线
            if (model.getNodeStatus() != null) {
                // 这里根据isActive字段来判断节点状态
                if ("online".equals(model.getNodeStatus())) {
                    queryWrapper.eq(AgentNodeInfo::getIsActive, true);
                } else if ("offline".equals(model.getNodeStatus())) {
                    queryWrapper.eq(AgentNodeInfo::getIsActive, false);
                }
            }

            // 根据操作系统查询 - 使用模糊匹配
            if (model.getOsName() != null) {
                queryWrapper.like(AgentNodeInfo::getOsName, model.getOsName());
            }

            // 根据客户端版本查询 - 使用模糊匹配
            if (model.getClientVersion() != null) {
                queryWrapper.like(AgentNodeInfo::getClientVersion, model.getClientVersion());
            }
        }

        // 执行分页查询
        service.page(page, queryWrapper);

        // 将实体列表转换为DTO列表
        List<AgentNodeInfoDTO> dtoList = page.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // 返回成功结果，包含DTO列表和总记录数
        return success(new PageResult<>(dtoList, page.getTotal()));
    }

    /**
     * 将实体转换为DTO
     *
     * 将AgentNodeInfo实体对象转换为AgentNodeInfoDTO数据传输对象
     * 包括基本属性复制、特定属性设置和数值类型转换
     *
     * @param entity AgentNodeInfo 实体对象
     * @return AgentNodeInfoDTO 数据传输对象
     */
    private AgentNodeInfoDTO convertToDTO(AgentNodeInfo entity) {
        // 如果实体为空，返回null
        if (entity == null) {
            return null;
        }

        AgentNodeInfoDTO dto = new AgentNodeInfoDTO();

        // 复制基本属性，排除需要特殊处理的字段
        BeanUtil.copyProperties(entity, dto, "nodeId", "nodeName", "nodeIp", "nodeStatus");

        // 设置特定属性
        dto.setNodeId(entity.getAgentId());
        dto.setNodeName(entity.getHostName()); // 使用主机名作为节点名称
        dto.setNodeIp(entity.getIp());
        dto.setNodeStatus(entity.getIsActive() != null && entity.getIsActive() ? "online" : "offline");

        // 转换数值类型 - CPU使用率
        if (entity.getCpuUsage() != null) {
            try {
                dto.setCpuUsage(Double.parseDouble(entity.getCpuUsage()));
            } catch (NumberFormatException e) {
                log.warn("CPU使用率格式转换失败: {}", entity.getCpuUsage());
                dto.setCpuUsage(0.0);
            }
        }

        // 转换数值类型 - 内存使用率
        if (entity.getMemoryUsage() != null) {
            try {
                dto.setMemoryUsage(Double.parseDouble(entity.getMemoryUsage()));
            } catch (NumberFormatException e) {
                log.warn("内存使用率格式转换失败: {}", entity.getMemoryUsage());
                dto.setMemoryUsage(0.0);
            }
        }

        // 转换数值类型 - 磁盘使用率
        if (entity.getDiskUsage() != null) {
            try {
                dto.setDiskUsage(Double.parseDouble(entity.getDiskUsage()));
            } catch (NumberFormatException e) {
                log.warn("磁盘使用率格式转换失败: {}", entity.getDiskUsage());
                dto.setDiskUsage(0.0);
            }
        }

        // 设置客户端版本和最后心跳时间
        dto.setClientVersion(entity.getClientVersion());
        dto.setLastHeartbeat(entity.getHeartbeatTimestamp());

        return dto;
    }

    /**
     * 实现批量查询方法
     *
     * 根据查询参数获取Agent节点列表
     * 实现NoPageQueryController接口的handlerBatchQuery方法
     *
     * @param param AgentNodeQueryParam 查询参数
     * @return List<AgentNodeInfo> 节点列表
     */
    @Override
    public List<AgentNodeInfo> handlerBatchQuery(AgentNodeQueryParam param) {
        // 构造查询条件
        LambdaQueryWrapper<AgentNodeInfo> queryWrapper = new LambdaQueryWrapper<>();

        // 根据查询参数构建查询条件
        if (param != null) {
            // 根据节点ID查询
            if (param.getAgentId() != null) {
                queryWrapper.eq(AgentNodeInfo::getAgentId, param.getAgentId());
            }

            // 根据主机名查询
            if (param.getHostName() != null) {
                queryWrapper.like(AgentNodeInfo::getHostName, param.getHostName());
            }

            // 根据IP查询
            if (param.getIp() != null) {
                queryWrapper.like(AgentNodeInfo::getIp, param.getIp());
            }

            // 根据节点状态查询
            if (param.getNodeStatus() != null) {
                if ("online".equals(param.getNodeStatus())) {
                    queryWrapper.eq(AgentNodeInfo::getIsActive, true);
                } else if ("offline".equals(param.getNodeStatus())) {
                    queryWrapper.eq(AgentNodeInfo::getIsActive, false);
                }
            }

            // 根据操作系统查询
            if (param.getOsName() != null) {
                queryWrapper.like(AgentNodeInfo::getOsName, param.getOsName());
            }

            // 根据客户端版本查询
            if (param.getClientVersion() != null) {
                queryWrapper.like(AgentNodeInfo::getClientVersion, param.getClientVersion());
            }
        }

        // 默认按照agentId排序
        queryWrapper.orderByAsc(AgentNodeInfo::getAgentId);

        // 执行查询并返回结果
        return service.list(queryWrapper);
    }

    /**
     * 处理单体查询数据
     *
     * 实现NoPageQueryController接口的handlerGetData方法
     * 在单体查询后对数据进行处理
     *
     * @param entity 实体对象
     */
    @Override
    public void handlerGetData(AgentNodeInfo entity) {
        // 如果需要对单体查询结果进行特殊处理，可以在这里实现
        // 目前没有特殊处理需求，保留方法供后续扩展
    }

    /**
     * 获取单个节点信息并转换为DTO
     *
     * 自定义方法，根据ID查询单个节点并返回DTO
     *
     * @param id 节点ID
     * @return ApiResult<AgentNodeInfoDTO> 节点DTO
     */
    @PreCheckPermission(value = "{}:view")
    @ApiOperationSupport(order = 30, author = "AutoGenerator")
    @ApiOperation(value = "获取单个节点信息")
    @SysLog
    @GetMapping("/info/{id}")
    public ApiResult<AgentNodeInfoDTO> getNodeInfo(@PathVariable("id") String id) {
        // 根据ID查询节点
        AgentNodeInfo entity = service.getById(id);

        // 处理单体查询数据
        handlerGetData(entity);

        // 将实体转换为DTO
        AgentNodeInfoDTO dto = convertToDTO(entity);

        return success(dto);
    }

    /**
     * 处理批量查询数据
     *
     * 实现NoPageQueryController接口的handlerBatchData方法
     * 在批量查询后对数据列表进行处理
     *
     * @param list 实体列表
     */
    @Override
    public void handlerBatchData(List<AgentNodeInfo> list) {
        // 如果需要对批量查询结果进行特殊处理，可以在这里实现
        // 目前没有特殊处理需求，保留方法供后续扩展
    }

    /**
     * 批量查询节点列表并转换为DTO
     *
     * 自定义方法，提供批量查询并返回DTO列表的功能
     *
     * @param param 查询参数
     * @return ApiResult<List<AgentNodeInfoDTO>> DTO列表
     */
    @PreCheckPermission(value = "{}:view")
    @ApiOperationSupport(order = 20, author = "AutoGenerator")
    @ApiOperation(value = "批量查询节点列表")
    @SysLog(response = false)
    @PostMapping("/list")
    public ApiResult<List<AgentNodeInfoDTO>> listDto(@RequestBody AgentNodeQueryParam param) {
        // 执行批量查询
        List<AgentNodeInfo> list = handlerBatchQuery(param);

        // 处理批量查询数据
        handlerBatchData(list);

        // 将实体列表转换为DTO列表
        List<AgentNodeInfoDTO> dtoList = list.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return success(dtoList);
    }

    /**
     * 刷新所有节点的状态信息
     *
     * 手动触发节点状态刷新，更新所有节点的在线/离线状态
     *
     * @return ApiResult<Boolean> 刷新结果
     */
    @ApiOperationSupport(order = 40, author = "AutoGenerator")
    @ApiOperation(value = "刷新节点状态")
    @SysLog
    @PostMapping("/refresh")
    public ApiResult<Boolean> refreshNodeStatus(@RequestBody JsonNode body) {
        try {
            // 调用心跳检查服务手动刷新节点状态
            // 发送redis stream任务
            // agent节点读取，并响应
            // master获取响应
            // master调用heartBeatCheckPeriod检查
            List<String> nodeIds = new ArrayList<>();
            JsonNode nodeIdsNode = body.get("nodeIds");
            if (nodeIdsNode.isArray()) {
                nodeIdsNode.forEach(node -> nodeIds.add(node.asText()));
            }
            // nodeIds为空无需更新agent节点的状态
            if (nodeIds.isEmpty()) {
                return ApiResult.success("刷新成功", true);
            }

            // 存储刷新失败的节点列表
            List<String> refreshFailNodeIds = new ArrayList<>();
            // 串行的向所有agent节点发布手动心跳命令，消费来自每个agent节点的响应
            // 如果节点非常多且子节点响应耗时长，极端情况下每个节点都会等待一定时间，将会导致本次controller响应超时
            // 考虑使用异步发送命令
            refreshFailNodeIds = refreshNodesAsync(nodeIds, 10);

            // 不管刷新结果如何，都手动触发一次主节点心跳检查
            heartBeatService.heartBeatCheckPeriod();

            if (!refreshFailNodeIds.isEmpty()) {
                log.error("部分节点: {} 刷新失败", refreshFailNodeIds);
                // 虽然部分节点刷新失败，但是主节点已经手动触发了一次心跳检查，刷新成功的节点依旧可以展示
                return ApiResult.success("部分节点刷新失败：" + refreshFailNodeIds, true);
            }
            return ApiResult.success("刷新成功", true);
        } catch (JsonProcessingException e) {
            log.error("刷新节点状态失败", e);
            return ApiResult.fail("刷新失败: " + e.getMessage(), false);
        }
    }

    /**
     * 异步刷新多个节点
     *
     * @param nodeIds 需要刷新的节点ID列表
     * @param timeoutSeconds 操作超时时间（秒）
     * @return 刷新失败的节点ID列表
     */
    public List<String> refreshNodesAsync(List<String> nodeIds, int timeoutSeconds) {
        // 创建线程安全的集合，用于存储刷新失败的节点ID
        List<String> failedNodeIds = Collections.synchronizedList(new ArrayList<>());

        // 为每个节点创建异步任务，并使用配置好的线程池执行
        List<CompletableFuture<Void>> futures = nodeIds.stream()
                .map(nodeId -> CompletableFuture.runAsync(() -> refreshSingleNode(nodeId, failedNodeIds), taskExecutor))
                .collect(Collectors.toList());

        try {
            // 等待所有异步任务完成，并设置超时时间
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);  // 使用完整类名引用TimeUnit
        } catch (InterruptedException e) {
            // 如果等待过程被中断，记录警告并重新设置中断标志
            log.warn("Node refresh operation was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // 如果任务执行过程中发生异常，记录错误
            log.error("Error during node refresh operation", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {  // 使用完整类名引用TimeoutException
            // 如果操作超时，记录警告
            log.warn("Node refresh operation timed out after {} seconds", timeoutSeconds);
        }

        // 返回刷新失败的节点ID列表
        return failedNodeIds;
    }

    /**
     * 刷新单个节点
     */
    private void refreshSingleNode(String nodeId, List<String> failedNodeIds) {
        String pubStreamkey = "pub:" + nodeId;
        String subStreamkey = "sub:" + nodeId;

        try {
            // 构建消息体
            HashMap<String, String> map = new HashMap<>();
            map.put("isUsing", "false");
            map.put("policy", "false");

            List<String> primaryKeyColumns = List.of("port", "protocol");

            PortRule portRule = PortRule.builder()
                    .port("2323")
                    .build();

            RedisCommandMessage<PortRule> build = RedisCommandMessage.<PortRule>builder()
                    .agentId(nodeId)
                    .ts(System.currentTimeMillis() / 1000)
                    .agentComponentType(RedisCommandMessage.ComponentType.FIREWALL)
                    .dataOpType(RedisCommandMessage.OperationType.OPTIONS)
                    .requestParams(map)
                    .primaryKeyColumns(primaryKeyColumns)
                    .data(Collections.<PortRule>emptyList())
                    .old(portRule)
                    .build();

            Map<String, String> messageMap = JsonMessageConverter.beanToMap(build);

            // 发布消息
            RecordId recordId = streamProducer.publishMessage(pubStreamkey, messageMap);

            // 获取响应，重试的方式获取响应，减少因为从节点响应不及时导致的失败
            Map<Object, Object> value = streamResponseService.getResponseEntry(nodeId, subStreamkey, pubStreamkey, recordId);

            // 判断是否刷新成功
            // 判断是否刷新成功，agent节点会向 名为"pub:" + nodeId 的StreamKey发送消息id为recordId的消息，其中value中包含status字段，如果status为200则刷新成功
            boolean respSuccess = value.containsKey("status") && value.get("status").equals("200");
            if (!respSuccess) {
                failedNodeIds.add(nodeId);
            }
        } catch (Exception e) {
            log.error("Failed to refresh node: {}", nodeId, e);
            failedNodeIds.add(nodeId);
        }
    }


}
