package com.yeahmobi.yscheduler.model.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.yeahmobi.yscheduler.model.WorkflowTaskDependency;
import com.yeahmobi.yscheduler.model.WorkflowTaskDependencyExample;
import com.yeahmobi.yscheduler.model.dao.WorkflowTaskDependencyDao;
import com.yeahmobi.yscheduler.model.service.WorkflowTaskDependencyService;

@Service
public class WorkflowTaskDependencyServiceImpl implements WorkflowTaskDependencyService {

    @Autowired
    private WorkflowTaskDependencyDao workflowTaskDependencyDao;

    public List<WorkflowTaskDependency> listByWorkflowDetailId(long workflowDetailId) {
        WorkflowTaskDependencyExample example = new WorkflowTaskDependencyExample();
        example.createCriteria().andWorkflowDetailIdEqualTo(workflowDetailId);
        return this.workflowTaskDependencyDao.selectByExample(example);
    }

    public void deleteByWorkflowDetailId(long workflowDetailId) {
        WorkflowTaskDependencyExample example = new WorkflowTaskDependencyExample();
        example.createCriteria().andWorkflowDetailIdEqualTo(workflowDetailId);
        this.workflowTaskDependencyDao.deleteByExample(example);
    }

    public void addDependencyTasks(long workflowDetailId, List<Long> dependencyTasks) {
        for (long dependencyTask : dependencyTasks) {
            WorkflowTaskDependency record = new WorkflowTaskDependency();
            record.setWorkflowDetailId(workflowDetailId);
            record.setDependencyTaskId(dependencyTask);
            Date time = new Date();
            record.setCreateTime(time);
            record.setUpdateTime(time);
            this.workflowTaskDependencyDao.insert(record);
        }
    }
}
