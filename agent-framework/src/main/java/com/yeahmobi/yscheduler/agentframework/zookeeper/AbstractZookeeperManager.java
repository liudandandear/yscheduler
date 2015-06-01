package com.yeahmobi.yscheduler.agentframework.zookeeper;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.KeeperException.NodeExistsException;

import com.alibaba.fastjson.JSON;
import com.yeahmobi.yscheduler.model.Attempt;
import com.yeahmobi.yscheduler.model.type.AttemptStatus;

/**
 * <pre>
 * 1. agents/<AgentId附加ip和port>/assignments/<AttemptId>/<AttemptJson>，<status>，cancelled
 * （这个List负责持久性维护。Agent启动需要知道自己的ip和port，然后确定自己的AgentId）（Agent启动时，除了恢复本地外，要check zk 的未结束的任务，对于未执行的要执行，排除本地扫描到的即执行中的，就是未执行的）
 * 2. agents/activeList/<agentId>
 * 3. agents/loaderbalanceIndex
 * </pre>
 *
 * @author atell
 */
public abstract class AbstractZookeeperManager {

    protected static final String    ENCODE                        = "UTF-8";
    protected static final String    PATH_AGENTS                   = "/agents";
    protected static final String    PATH_ACTIVE_LIST              = PATH_AGENTS + "/activeList";

    protected static final int       DEFAULT_MAX_SLEEP_TIME        = 30000;
    protected static final int       DEFAULT_BASE_SLEEP_TIME       = 500;

    protected static final int       DEFAULT_SESSION_TIMEOUT_MS    = 60 * 1000;
    protected static final int       DEFAULT_CONNECTION_TIMEOUT_MS = 15 * 1000;

    protected static final String    NAMESPACE                     = "yscheduler";
    protected static final int       MAX_RETRY_VALUE               = 29;

    protected final CuratorFramework client;

    public AbstractZookeeperManager(String rootPath, String zkUrl, int sessionTimeoutMs, int connectionTimeoutMs,
                                    RetryPolicy retryPolicy) throws Exception {
        // 构造并启动zk client
        if (rootPath == null) {
            rootPath = NAMESPACE;
        }
        this.client = CuratorFrameworkFactory.builder().connectString(zkUrl).sessionTimeoutMs(sessionTimeoutMs).connectionTimeoutMs(connectionTimeoutMs).namespace(rootPath).retryPolicy(retryPolicy).build();
        this.client.start();

        // init zk base path
        try {
            this.client.create().creatingParentsIfNeeded().forPath(PATH_ACTIVE_LIST);
        } catch (Exception e) {
            if (e instanceof NodeExistsException) {
                // ignore
            } else {
                throw e;
            }
        }
    }

    /**
     * 获取/agents/下的agentIds
     *
     * @throws Exception
     */
    protected List<Long> _getAgentIds() throws Exception {
        List<Long> list = new ArrayList<Long>();
        String path = PATH_AGENTS;
        List<String> agentIdStrs = this.client.getChildren().forPath(path);
        if (agentIdStrs != null) {
            for (String agentIdStr : agentIdStrs) {
                long agentId = Long.parseLong(agentIdStr);
                list.add(agentId);
            }
        }
        return list;
    }

    protected List<Long> getAttemptIds(long agentId) throws Exception {
        return getAttemptIds(agentId, null);
    }

    protected List<Long> getAttemptIds(long agentId, CuratorWatcher listener0) throws Exception {
        List<Long> list = new ArrayList<Long>();
        String path = PATH_AGENTS + "/" + agentId + "/assignments/";
        List<String> attemptIdStrs;
        if (listener0 != null) {
            attemptIdStrs = this.client.getChildren().usingWatcher(listener0).forPath(path);
        } else {
            attemptIdStrs = this.client.getChildren().forPath(path);
        }
        if (attemptIdStrs != null) {
            for (String attemptIdStr : attemptIdStrs) {
                long attemptId = Long.parseLong(attemptIdStr);
                list.add(attemptId);
            }
        }
        return list;
    }

    protected Attempt _getAttempt(long agentId, long attemptId) throws Exception {
        String path = PATH_AGENTS + "/" + agentId + "/assignments/" + attemptId;
        byte[] bytes = this.client.getData().forPath(path);
        if (bytes != null) {
            return fromJson(new String(bytes, ENCODE));
        }
        return null;
    }

    protected AttemptStatus getAttemptStatus(long agentId, long attemptId) throws Exception {
        return getAttemptStatus(agentId, attemptId, null);
    }

    protected AttemptStatus getAttemptStatus(long agentId, long attemptId, CuratorWatcher watcher) throws Exception {
        String statusPath = this.getStatusPath(agentId, attemptId);
        AttemptStatus status;
        if (watcher != null) {
            status = AttemptStatus.valueOf(Integer.parseInt(new String(
                                                                       this.client.getData().usingWatcher(watcher).forPath(statusPath))));
        } else {
            status = AttemptStatus.valueOf(Integer.parseInt(new String(this.client.getData().forPath(statusPath))));
        }
        return status;
    }

    protected String getAssignmentsRoot(long agentId) {
        return "/agents/" + agentId + "/assignments/";
    }

    protected String getStatusPath(long agentId, long attemptId) {
        String path = getAttemptPath(agentId, attemptId);
        String statusPath = path + "/status";
        return statusPath;
    }

    protected String getAttemptPath(long agentId, long attemptId) {
        return PATH_AGENTS + "/" + agentId + "/assignments/" + attemptId;
    }

    protected String getCancelPath(long agentId, long attemptId) {
        String path = getAttemptPath(agentId, attemptId);
        String cancelPath = path + "/cancelled";
        return cancelPath;
    }

    protected byte[] toBytes(String json) {
        try {
            return json.getBytes(ENCODE);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected String toJson(Attempt attempt) {
        return JSON.toJSONString(attempt);
    }

    protected Attempt fromJson(String json) {
        return JSON.parseObject(json, Attempt.class);
    }

    protected Attempt getAttempt(long agentId, long attemptId) throws Exception {
        String attemptPath = this.getAttemptPath(agentId, attemptId);
        Attempt attempt = fromJson(new String(this.client.getData().forPath(attemptPath)));
        return attempt;
    }

}