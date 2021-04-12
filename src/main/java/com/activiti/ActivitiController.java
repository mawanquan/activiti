package com.activiti;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import com.util.ToWeb;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ManagementService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmActivity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.util.R;


/**
 * @ClassName: ActivitiController
 * @Description: (工作流controller)
 * @author mawanquan
 * 
 */
@RestController
@RequestMapping("activiti")
public class ActivitiController{
 
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    ProcessEngineConfiguration processEngineConfiguration;
    @Autowired
    RepositoryService repositoryService;

    /**
     * @Description:  (获取activiti属性)
     * @return
     * @author:mawanquan  
     * @throws
     */


    @RequestMapping("getVersion")
    public R getVersion() {
    	ManagementService repositoryService = processEngine.getManagementService();
    	Map<String, String> map = repositoryService.getProperties();
    	return R.ok().put("version", map);
    }
    
    /**
     * @Description:  (创建模型)
     * @param name 名称
     * @param description 描述
     * @param key key
     * @return
     * @throws UnsupportedEncodingException R    返回类型
     * @date 2020年4月11日 上午9:50:10
     * @author:mawanquan  
     * @throws
     */
	@RequestMapping("createModel")
    public Object newModel(String name,String description,String key) throws UnsupportedEncodingException {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        //初始化一个空模型
        Model model = repositoryService.newModel();
        //设置一些默认信息
        int revision = 1;  // 版本号
        
        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);
        modelNode.put("key", key);
 
        model.setName(name); // 流程名称
        model.setKey(key); // 流程key
        model.setMetaInfo(modelNode.toString());
 
        repositoryService.saveModel(model);
        String id = model.getId();
 
        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace", "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
//        return R.ok().put("modelId", id);
        return ToWeb.buildResult().redirectUrl("/editor?modelId="+id);
    }
 
    /**
     * @Title: deleteModel
     * @Description:  (删除model)
     * @param id
     * @return R    返回类型
     * @date 2020年4月11日 上午9:50:37
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("deleteModel")
    public R deleteModel(String id){
        RepositoryService repositoryService = processEngine.getRepositoryService();
        repositoryService.deleteModel(id);
        return R.ok();
    }
    
    /**
     * @Title: deleteModelForBatch
     * @Description:  (批量删除model)
     * @param id
     * @return R    返回类型
     * @date 2020年4月11日 上午9:50:47
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("deleteModelForBatch")
    public R deleteModelForBatch(String id){
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	String[] ids = id.split(",");
    	for (int i = 0; i < ids.length; i++) {
    		repositoryService.deleteModel(ids[i]);
		}
    	return R.ok();
    }
 
    /**
     * @Title: deploy
     * @Description:  (部署模型为流程定义)
     * @param id
     * @return
     * @throws Exception R    返回类型
     * @date 2020年4月11日 上午9:51:04
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("deployment")
    public R deploy(String id) throws Exception {
        //获取模型
        RepositoryService repositoryService = processEngine.getRepositoryService();
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());
 
        if (bytes == null) {
            return R.error("模型数据为空，请先设计流程并成功保存，再进行发布。");
        }
        JsonNode modelNode = new ObjectMapper().readTree(bytes);
 
        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            return R.error("数据模型不符要求，请至少设计一条主线流程。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);
 
        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);
        return R.ok();
    }
    
    /**
     * @Title: loadByDeployment
     * @Description:  ((获取流程资源 xml/png)
     * @param processDefinitionId
     * @param resourceType
     * @param response void    返回类型
     * @date 2020年4月11日 上午9:51:24
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("resource") 
    public void loadByDeployment(String processDefinitionId, String resourceType, HttpServletResponse response){ 
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
    			.processDefinitionId(processDefinitionId)
    			.singleResult(); 
    	String resourceName = ""; 
    	if (resourceType.equals("image")) { 
    		resourceName = processDefinition.getDiagramResourceName(); 
    	} else if (resourceType.equals("xml")) { 
    		resourceName = processDefinition.getResourceName(); 
    	} 
    	InputStream resourceAsStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(), resourceName); 
    	byte[] b = new byte[1024]; 
    	int len = -1; 
    	try { 
    		while ((len = resourceAsStream.read(b, 0, 1024)) != -1) { 
    			response.getOutputStream().write(b, 0, len); 
    		} 
    	} catch (IOException e) { 
    		e.printStackTrace();
    	}
    }
    
    /**
     * @Title: suspendProcessDefinitionById
     * @Description:  (挂起流程)
     * @param processDefinitionId
     * @return R    返回类型
     * @date 2020年4月11日 上午9:51:40
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("suspendProcessDefinitionById")
    public R suspendProcessDefinitionById(String processDefinitionId) {
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	repositoryService.suspendProcessDefinitionById(processDefinitionId);
    	return R.ok();
    }
    
    /**
     * @Title: activateProcessDefinitionById
     * @Description:  (激活)
     * @param processDefinitionId
     * @return R    返回类型
     * @date 2020年4月11日 上午9:52:02
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("activateProcessDefinitionById")
    public R activateProcessDefinitionById(String processDefinitionId) {
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	repositoryService.activateProcessDefinitionById(processDefinitionId);
    	return R.ok();
    }
    
    /**
     * @Title: deleteProcessDeploymentId
     * @Description:  (删除部署流程)
     * @param processDefinitionId
     * @param deploymentId
     * @param cascade
     * @return R    返回类型
     * @date 2020年4月11日 上午9:52:13
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("deleteProcessDeploymentId")
    @Transactional
    public R deleteProcessDeploymentId(String processDefinitionId,String deploymentId,boolean cascade) {
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	RuntimeService runtimeService = processEngine.getRuntimeService();
    	// 判断流程是否结束
    	List<ProcessInstance> list = runtimeService.createProcessInstanceQuery().deploymentId(deploymentId).list();
    	if(!cascade) { // 单个删除
    		if(null == list || list.size() == 0) { // 流程 暂无启动 可以删除
    			repositoryService.deleteDeployment(deploymentId);
    			return R.ok("删除成功");
    		}else {
    			return R.ok("流程已启动，请勿删除");
    		}
    	}
    	repositoryService.deleteDeployment(deploymentId,true); // 级联删除
    	return R.ok("删除成功");
    }
    
    /**
     * @Title: modelList
     * @Description:  (分页查询model基本信息)
     * @return R    返回类型
     * @date 2020年4月11日 上午9:52:25
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/modelList")
    public R modelList(){
    	
        return R.ok().put("page", "");
    }
    
    /**
     * @Title: deploymentList
     * @Description:  (分页查询部署列表)
     * @return R    返回类型
     * @date 2020年4月11日 上午9:52:34
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/deploymentList")
    public R deploymentList(int firstResult, int maxResults){
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	List<Deployment> list = repositoryService.createDeploymentQuery().listPage(firstResult, maxResults);
    	return R.ok().put("page", list);
    }
    
    /**
     * @Title: findUndoneTasks
     * @Description:  (查询待办列表)
     * @param assignee 待办用户
     * @param processDefinitionKey 流程key
     * @return R    返回类型
     * @date 2020年4月11日 上午10:07:16
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/findUndoneTasks")
    public R findUndoneTasks(String assignee,String processDefinitionKey){
    	TaskService taskService = processEngine.getTaskService();
    	List<Task> list = taskService.createTaskQuery().taskAssignee(assignee).processDefinitionKey(processDefinitionKey).list();
    	return R.ok().put("page", list);
    }
    
    /**
     * @Title: findDoneTasks
     * @Description:  (查询已处理任务)
     * @param assignee
     * @param processDefinitionKey
     * @return R    返回类型
     * @date 2020年4月11日 上午10:12:04
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/findDoneTasks")
    public R findDoneTasks(String assignee,String processDefinitionKey){
    	HistoryService historyService = processEngine.getHistoryService();
    	List<HistoricTaskInstance> list = historyService.createHistoricTaskInstanceQuery().taskAssignee(assignee).list();
    	return R.ok().put("page", list);
    }
    
    /**
     * @Title: findRunningProcess
     * @Description:  (查询正在运行的流程)
     * @return R    返回类型
     * @date 2020年4月11日 上午10:14:35
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/findRunningProcess")
    public R findRunningProcess(){
    	RuntimeService runtimeService  = processEngine.getRuntimeService();
    	List<ProcessInstance> list = runtimeService.createProcessInstanceQuery().list();
    	return R.ok().put("page", list);
    }
    
    /**
     * @Title: findFinishedProcess
     * @Description:  (查询已完成的流程)
     * @return R    返回类型
     * @date 2020年4月11日 上午10:17:32
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("/findFinishedProcess")
    public R findFinishedProcess(){
    	HistoryService historyService = processEngine.getHistoryService();
    	List<HistoricProcessInstance> list = historyService.createHistoricProcessInstanceQuery().finished().list();
    	return R.ok().put("page", list);
    }
    
    /**
     * @Title: complete
     * @Description:  (完成流程任务)
     * @param taskId
     * @return R    返回类型
     * @date 2020年4月11日 上午10:20:44
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("complete")
    @Transactional
    public R complete(String taskId) {
    	Map<String, Object> variables = new HashMap<String, Object>();
    	TaskService taskService = processEngine.getTaskService();
    	taskService.complete(taskId,variables);
    	return R.ok();
    }
    
    /**
     * @Title: deploymentByUpload
     * @Description:  (上传流程文件)
     * @param exportDir
     * @param file
     * @return R    返回类型
     * @date 2020年4月11日 上午10:20:56
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("deploymentByUpload")
    public R deploymentByUpload(String exportDir, @RequestParam(value = "file", required = false) MultipartFile file) {
        String fileName = file.getOriginalFilename();
        RepositoryService repositoryService = processEngine.getRepositoryService();
        try {
            InputStream fileInputStream = file.getInputStream();
            String extension = FilenameUtils.getExtension(fileName);
            if (extension.equals("zip") || extension.equals("bar")) {
                ZipInputStream zip = new ZipInputStream(fileInputStream);
               repositoryService.createDeployment().addZipInputStream(zip).deploy();
            } else {
                repositoryService.createDeployment().addInputStream(fileName, fileInputStream).deploy();
            }

        } catch (Exception e) {
        	e.printStackTrace();
        	return R.error("部署失败");
        }
        return R.ok("部署成功");
    }
    
    /**
     * @Title: convertToModel
     * @Description:  (转换模型)
     * @param processDefinitionId
     * @return R    返回类型
     * @date 2020年4月11日 下午4:11:29
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("convertToModel")
    public R convertToModel(String processDefinitionId){
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	try {
    		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
    				.processDefinitionId(processDefinitionId)
    				.singleResult();

    		InputStream bpmnStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(),
    				processDefinition.getResourceName());
    		XMLInputFactory xif = XMLInputFactory.newInstance();
    		InputStreamReader in = new InputStreamReader(bpmnStream, "UTF-8");
    		XMLStreamReader xtr = xif.createXMLStreamReader(in);
    		BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);

    		BpmnJsonConverter converter = new BpmnJsonConverter();
    		com.fasterxml.jackson.databind.node.ObjectNode modelNode = converter.convertToJson(bpmnModel);
    		Model modelData = repositoryService.newModel();
    		modelData.setKey(processDefinition.getKey());
    		modelData.setName(processDefinition.getResourceName());
    		modelData.setCategory(processDefinition.getDeploymentId());

    		ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
    		modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, processDefinition.getName());
    		modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);
    		modelObjectNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, processDefinition.getDescription());
    		modelData.setMetaInfo(modelObjectNode.toString());
    		repositoryService.saveModel(modelData);
    		repositoryService.addModelEditorSource(modelData.getId(), modelNode.toString().getBytes("utf-8"));
    	} catch (Exception e) {
    		e.printStackTrace();
    		return R.error("转换失败");
		}
        return R.ok("转换成功");
    }
    
    
    /**
     * @Title: readResource
     * @Description:  (读取带跟踪的图片"节点带显示红框")
     * @param executionId
     * @param processDefinitionId
     * @param response
     * @throws Exception void    返回类型
     * @date 2020年4月11日 下午4:11:49
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("readResource")
    public void readResource(String executionId,String processDefinitionId, HttpServletResponse response)throws Exception {
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	RuntimeService runtimeService = processEngine.getRuntimeService();
    	
    	BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
    	
    	processEngineConfiguration = processEngine.getProcessEngineConfiguration();
    	Context.setProcessEngineConfiguration((ProcessEngineConfigurationImpl) processEngineConfiguration);
    	
    	ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
    	InputStream imageStream = null;
    	if(null != executionId && !"".equals(executionId)) { // 流程未结束
    		List<String> activeActivityIds = runtimeService.getActiveActivityIds(executionId);
    		
    		// 解决图片中文显示乱码
    		imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", activeActivityIds,
    				Collections.<String> emptyList(),
    				processEngineConfiguration.getActivityFontName(),
    				processEngineConfiguration.getLabelFontName(),
    				processEngineConfiguration.getAnnotationFontName(), null,1);
    	}else {
    		imageStream = diagramGenerator.generateDiagram(bpmnModel,"png",
    				processEngineConfiguration.getActivityFontName(),
    				processEngineConfiguration.getLabelFontName(),
    				processEngineConfiguration.getAnnotationFontName(),null);
    	}
    	// 输出资源内容到相应对象
    	byte[] b = new byte[1024];
    	int len;
    	while ((len = imageStream.read(b, 0, 1024)) != -1) {
    		response.getOutputStream().write(b, 0, len);
    	}
    }
    
    
    
    /**
     * @Title: rollBackWorkFlow
     * @Description:  (退回上一个节点=撤回)
     * @param taskId
     * @return String    返回类型
     * @date 2019年3月25日 上午11:11:19
     * @author:WangZhu  
     * @throws
     */
    @RequestMapping("rollBackWorkFlow")
    @Transactional
    public R rollBackWorkFlow(String taskId,  String executionId) {
    	TaskService taskService = processEngine.getTaskService();
    	HistoryService historyService = processEngine.getHistoryService();
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	RuntimeService runtimeService = processEngine.getRuntimeService();
    	try {
    		
    		Map<String, Object> variables;
    		// 取得当前任务.当前任务节点
    		HistoricTaskInstance currTask = historyService
    				.createHistoricTaskInstanceQuery()
    				.taskId(taskId)
    				.singleResult();
    		// 取得流程实例，流程实例
    		ProcessInstance instance = runtimeService
    				.createProcessInstanceQuery()
    				.processInstanceId(currTask.getProcessInstanceId())
    				.singleResult();
    		if (instance == null) {
    			return R.error("流程已经结束，无法撤回。");
    		}
    		variables = instance.getProcessVariables();
    		// 取得流程定义
    		ProcessDefinitionEntity definition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
    				.getDeployedProcessDefinition(currTask.getProcessDefinitionId());
    		if (definition == null) {
    			return R.error("流程定义未找到，无法撤回。");
    		}
    		
    		// 获取历史连线
    		HistoricActivityInstance activityInstances = historyService.createHistoricActivityInstanceQuery()
    				.executionId(executionId)
    				.orderByHistoricActivityInstanceEndTime()
    				.desc().list().get(0);
    		
    		if(activityInstances == null) {
    			return R.error("流程历史未找到，无法撤回。");
    		}
    		
    		// 取得上一步活动
    		ActivityImpl currActivity = ((ProcessDefinitionImpl) definition)
    				.findActivity(currTask.getTaskDefinitionKey());
    		// 当前节点流入的连线
    		List<PvmTransition> nextTransitionList = currActivity.getIncomingTransitions();
    		// 清除当前活动的出口
    		List<PvmTransition> oriPvmTransitionList = new ArrayList<PvmTransition>();
    		// 新建一个节点连线关系集合

    		List<PvmTransition> pvmTransitionList = currActivity.getOutgoingTransitions();
    		
    		for (PvmTransition pvmTransition : pvmTransitionList) {
    			oriPvmTransitionList.add(pvmTransition);
    		}
    		pvmTransitionList.clear();
    		
    		// 建立新出口
    		List<TransitionImpl> newTransitions = new ArrayList<TransitionImpl>();
    		for (PvmTransition nextTransition : nextTransitionList) {
    			PvmActivity nextActivity = nextTransition.getSource();
    			if(activityInstances.getActivityId().equals(nextActivity.getId())) { // 匹配上一步连线
    				ActivityImpl nextActivityImpl = ((ProcessDefinitionImpl) definition)
    						.findActivity(nextActivity.getId());
    				TransitionImpl newTransition = currActivity
    						.createOutgoingTransition();
    				newTransition.setDestination(nextActivityImpl);
    				newTransitions.add(newTransition);
    			}
    		}
    		// 完成任务
    		List<Task> tasks = taskService.createTaskQuery()
    				.processInstanceId(instance.getId())
    				.taskDefinitionKey(currTask.getTaskDefinitionKey()).list();
    		
    		Map<String, Object> map = new HashMap<String, Object>();
    		for (Task task : tasks) {
    			taskService.setVariableLocal(task.getId(), "recall", "recall"); // 设置撤回变量
    			taskService.complete(task.getId(), variables);
    			// 修改流程历史变量taskId
    			HistoricVariableInstance variableInstance = historyService.createHistoricVariableInstanceQuery()
    					.taskId(task.getId()).singleResult();
    			String vid = variableInstance.getId(); // 变量id
    			map.put("id", vid);
    			map.put("taskId", task.getParentTaskId());
    			historyService.deleteHistoricTaskInstance(task.getId()); // 删除
    		}
    		// 恢复方向
    		for (TransitionImpl transitionImpl : newTransitions) {
    			currActivity.getOutgoingTransitions().remove(transitionImpl);
    		}
    		for (PvmTransition pvmTransition : oriPvmTransitionList) {
    			pvmTransitionList.add(pvmTransition);
    		}
    		return R.ok("撤回成功");
    	} catch (Exception e) {
    		return R.error("撤回失败");
    	}
    }
    
    /**
     * @Title: getLastActivity
     * @Description:  (查询上一节点类型)
     * @param taskId
     * @return R    返回类型
     * @date 2019年4月1日 下午2:45:30
     * @author:WangZhu  
     * @throws
     */
    @RequestMapping("getLastActivity")
    public R getLastActivity(String taskId) {
    	HistoryService historyService = processEngine.getHistoryService();
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	// 取得当前任务.当前任务节点
		HistoricTaskInstance currTask = historyService.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
		// 取得流程定义
		ProcessDefinitionEntity definitionEntity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
				.getDeployedProcessDefinition(currTask.getProcessDefinitionId());
    	 
    	// 取得上一步活动
		ActivityImpl currActivity = ((ProcessDefinitionImpl) definitionEntity).findActivity(currTask.getTaskDefinitionKey());
		
		String type = ""; // 上一节点类型 
		List<PvmTransition> outTransitions = currActivity.getOutgoingTransitions();// 获取从某个节点出来的所有线路
		for (PvmTransition tr : outTransitions) { 
			PvmActivity ac = tr.getDestination(); // 获取线路的终点节点 
			type = (String) ac.getProperty("type"); 
		} 
    	return R.ok().put("type", type);
    }
    
    /**
     * @Title: getNextActivity
     * @Description:  (获取下一节点类型) exclusiveGateway：网关  userTask：用户任务  endEvent：结束节点
     * @param activityId
     * @param processDefinitionId
     * @return R    返回类型
     * @date 2019年3月26日 下午3:52:32
     * @author:WangZhu  
     * @throws
     */
    @RequestMapping("getNextActivity")
    public R getNextActivity(String activityId,String processDefinitionId) {
    	RepositoryService repositoryService = processEngine.getRepositoryService();
    	ProcessDefinitionEntity definitionEntity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
    			.getDeployedProcessDefinition(processDefinitionId);

    	List<ActivityImpl> activitiList =  definitionEntity.getActivities(); // 获取流程所有节点信息
    	 
    	String type = ""; // 下一些点类型
    	for (ActivityImpl activityImpl : activitiList) { 
    		String id = activityImpl.getId(); 
    		if (activityId.equals(id)) { 
    			List<PvmTransition> outTransitions = activityImpl.getOutgoingTransitions();// 获取从某个节点出来的所有线路
    			for (PvmTransition tr : outTransitions) { 
    				PvmActivity ac = tr.getDestination(); // 获取线路的终点节点 
    				type = (String) ac.getProperty("type"); 
    			} 
    			break; 
    		} 
    	}

    	return R.ok().put("type", type);
    }
    
    /**
     * @Title: export
     * @Description:  (导出文件)
     * @param modelId
     * @param response void    返回类型
     * @date 2020年4月11日 上午11:38:12
     * @author:mawanquan  
     * @throws
     */
    @RequestMapping("export")
	public void export(String modelId,HttpServletResponse response) {
		RepositoryService repositoryService = processEngine.getRepositoryService();
		ByteArrayInputStream in = null;
		ServletOutputStream out = null;
		try {
			Model modelData = repositoryService.getModel(modelId);
			BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
			byte[] modelEditorSource = repositoryService.getModelEditorSource(modelData.getId());
			JsonNode editorNode = new ObjectMapper().readTree(modelEditorSource);
			BpmnModel bpmnModel = jsonConverter.convertToBpmnModel(editorNode);
			// 处理异常
			if (bpmnModel.getMainProcess() == null) {
				response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
				response.getOutputStream().println("没有主线程无法导出！");
				response.flushBuffer();
				return;
			}
			String mainProcessId = bpmnModel.getMainProcess().getId();
			BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
			byte[] exportBytes = xmlConverter.convertToXML(bpmnModel);
			String filename = mainProcessId + ".bpmn20.xml";
			in = new ByteArrayInputStream(exportBytes);
			
			response.setContentType("application/force-download");// 设置强制下载不打开
            response.addHeader("Content-Disposition", "attachment;fileName=" + new String(filename.getBytes("utf-8"), "ISO-8859-1"));// 设置文件名
			out = response.getOutputStream();
			byte[] data = new byte[in.available()];
			int count = 0;
			while ((count = in.read(data)) > 0) {
				out.write(data,0,count);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
			if (out != null) {
				try {
					out.close();
					out.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}