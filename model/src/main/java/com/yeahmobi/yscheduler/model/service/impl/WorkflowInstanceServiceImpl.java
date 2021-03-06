package com.yeahmobi.yscheduler.model.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yeahmobi.yscheduler.common.Constants;
import com.yeahmobi.yscheduler.common.CrontabUtils;
import com.yeahmobi.yscheduler.common.Paginator;
import com.yeahmobi.yscheduler.model.Team;
import com.yeahmobi.yscheduler.model.TeamWorkflowInstanceStatus;
import com.yeahmobi.yscheduler.model.Workflow;
import com.yeahmobi.yscheduler.model.WorkflowInstance;
import com.yeahmobi.yscheduler.model.WorkflowInstanceExample;
import com.yeahmobi.yscheduler.model.WorkflowInstanceExample.Criteria;
import com.yeahmobi.yscheduler.model.common.Query;
import com.yeahmobi.yscheduler.model.dao.WorkflowInstanceDao;
import com.yeahmobi.yscheduler.model.service.TeamService;
import com.yeahmobi.yscheduler.model.service.TeamWorkflowStatusInstanceService;
import com.yeahmobi.yscheduler.model.service.WorkflowInstanceService;
import com.yeahmobi.yscheduler.model.service.WorkflowService;
import com.yeahmobi.yscheduler.model.type.WorkflowInstanceStatus;

@Service
public class WorkflowInstanceServiceImpl implements WorkflowInstanceService {

    @Autowired
    private WorkflowInstanceDao               workflowInstanceDao;

    @Autowired
    private WorkflowService                   workflowService;

    @Autowired
    private TeamService                       teamService;

    @Autowired
    private TeamWorkflowStatusInstanceService teamWorkflowStatusInstanceService;

    public WorkflowInstance get(long id) {
        return this.workflowInstanceDao.selectByPrimaryKey(id);
    }

    public List<WorkflowInstance> list(Query query, long workflowId, int pageNum, Paginator paginator) {
        WorkflowInstanceExample example = new WorkflowInstanceExample();

        Criteria criteria = example.or();

        query(query, criteria);

        criteria.andWorkflowIdEqualTo(workflowId);

        int count = this.workflowInstanceDao.countByExample(example);

        paginator.setItemsPerPage(Constants.PAGE_SIZE);
        paginator.setItems(count);
        paginator.setPage(pageNum);

        int offset = paginator.getBeginIndex() - 1;
        int limit = Constants.PAGE_SIZE;

        RowBounds rowBounds = new RowBounds(offset, limit);

        example.setOrderByClause("id DESC");
        return this.workflowInstanceDao.selectByExampleWithRowbounds(example, rowBounds);
    }

    private void query(Query query, Criteria criteria) {
        if (query.getWorkflowScheduleType() != null) {
            switch (query.getWorkflowScheduleType()) {
                case AUTO:
                    criteria.andScheduleTimeIsNotNull();
                    break;
                case MANAUAL:
                    criteria.andScheduleTimeIsNull();
                    break;
            }
        }
        if (query.getWorkflowInstanceStatus() != null) {
            criteria.andStatusEqualTo(query.getWorkflowInstanceStatus());
        }
    }

    public List<WorkflowInstance> listAll(long workflowId) {
        WorkflowInstanceExample example = new WorkflowInstanceExample();

        example.createCriteria().andWorkflowIdEqualTo(workflowId);
        example.setOrderByClause("id DESC");

        return this.workflowInstanceDao.selectByExample(example);
    }

    public void save(WorkflowInstance instance) {
        Date time = new Date();
        instance.setCreateTime(time);
        instance.setUpdateTime(time);
        this.workflowInstanceDao.insert(instance);

        // 如果是公共工作流的instance，则需要为所有team增加teamWorkflowInstanceStatus
        Long workflowId = instance.getWorkflowId();
        Workflow workflow = this.workflowService.get(workflowId);
        Boolean common = workflow.getCommon();
        if ((common != null) && common) {
            List<Team> teams = this.teamService.list();
            for (Team team : teams) {
                TeamWorkflowInstanceStatus teamWorkflowInstanceStatus = new TeamWorkflowInstanceStatus();
                teamWorkflowInstanceStatus.setStatus(WorkflowInstanceStatus.INITED);
                teamWorkflowInstanceStatus.setTeamId(team.getId());
                teamWorkflowInstanceStatus.setWorkflowId(workflowId);
                teamWorkflowInstanceStatus.setWorkflowInstanceId(instance.getId());
                this.teamWorkflowStatusInstanceService.save(teamWorkflowInstanceStatus);
            }
        }

    }

    @SuppressWarnings("unchecked")
    public List<WorkflowInstance> getAllRunning(boolean common) {
        List<Workflow> workflows = null;

        if (common) {
            workflows = this.workflowService.listAllCommon();
            if ((workflows == null) || workflows.isEmpty()) {
                return new ArrayList<WorkflowInstance>();
            }
        } else {
            workflows = this.workflowService.listAllPrivate();
            if ((workflows == null) || workflows.isEmpty()) {
                return new ArrayList<WorkflowInstance>();
            }
        }

        List<Long> workflowIds = new ArrayList<Long>();

        if (workflows != null) {
            workflowIds.addAll(CollectionUtils.collect(workflows, new Transformer() {

                public Object transform(Object input) {
                    return ((Workflow) input).getId();
                }
            }));
        }

        WorkflowInstanceExample example = new WorkflowInstanceExample();
        example.createCriteria().andStatusEqualTo(WorkflowInstanceStatus.RUNNING).andWorkflowIdIn(workflowIds);

        return this.workflowInstanceDao.selectByExample(example);
    }

    public List<WorkflowInstance> getAllInits() {
        WorkflowInstanceExample example = new WorkflowInstanceExample();
        example.createCriteria().andStatusEqualTo(WorkflowInstanceStatus.INITED);

        return this.workflowInstanceDao.selectByExample(example);
    }

    public void updateStatus(Long instanceId, WorkflowInstanceStatus status) {
        Date now = new Date();
        WorkflowInstance record = new WorkflowInstance();
        record.setId(instanceId);
        record.setStatus(status);
        record.setUpdateTime(now);

        if (status.isCompleted()) {
            record.setEndTime(now);
        } else if (WorkflowInstanceStatus.RUNNING.equals(status)) {
            record.setStartTime(now);
        }

        this.workflowInstanceDao.updateByPrimaryKeySelective(record);
    }

    public boolean existUncompleted(long workflowId) {
        WorkflowInstanceExample example = new WorkflowInstanceExample();
        example.createCriteria().andWorkflowIdEqualTo(workflowId).andStatusIn(WorkflowInstanceStatus.getUncompleted());
        return this.workflowInstanceDao.countByExample(example) > 0;
    }

    public WorkflowInstance getLast(Workflow workflow, WorkflowInstance workflowInstance) {
        if ((workflow == null) || (workflowInstance == null) || (workflowInstance.getScheduleTime() == null)) {
            return null;
        }
        WorkflowInstanceExample example = new WorkflowInstanceExample();
        Criteria criteria = example.createCriteria().andWorkflowIdEqualTo(workflow.getId()).andScheduleTimeIsNotNull();
        if (workflowInstance.getId() != null) {
            criteria.andIdLessThan(workflowInstance.getId());
        }
        example.setOrderByClause("id DESC");
        RowBounds rowBounds = new RowBounds(0, 1);
        List<WorkflowInstance> workflowInstances = this.workflowInstanceDao.selectByExampleWithRowbounds(example,
                                                                                                         rowBounds);

        if (!workflowInstances.isEmpty()) {
            WorkflowInstance result = workflowInstances.get(0);
            if (CrontabUtils.validateLastScheduleTime(workflow.getCrontab(), result.getScheduleTime(),
                                                      workflowInstance.getScheduleTime())) {
                return result;
            }
        }
        return null;
    }

    public List<WorkflowInstance> list(List<Long> ids) {
        WorkflowInstanceExample example = new WorkflowInstanceExample();
        example.createCriteria().andIdIn(ids);
        example.setOrderByClause("id desc");
        return this.workflowInstanceDao.selectByExample(example);
    }

}
