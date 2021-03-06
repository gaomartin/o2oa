package com.x.processplatform.assemble.surface.jaxrs.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.JsonElement;
import com.x.base.core.container.EntityManagerContainer;
import com.x.base.core.entity.JpaObject;
import com.x.base.core.entity.dataitem.DataItemConverter;
import com.x.base.core.entity.dataitem.ItemCategory;
import com.x.base.core.project.annotation.FieldDescribe;
import com.x.base.core.project.bean.WrapCopier;
import com.x.base.core.project.bean.WrapCopierFactory;
import com.x.base.core.project.gson.GsonPropertyObject;
import com.x.base.core.project.http.EffectivePerson;
import com.x.base.core.project.jaxrs.StandardJaxrsAction;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.base.core.project.organization.OrganizationDefinition;
import com.x.base.core.project.tools.ListTools;
import com.x.base.core.project.tools.SortTools;
import com.x.processplatform.assemble.surface.Business;
import com.x.processplatform.assemble.surface.WorkControl;
import com.x.processplatform.core.entity.content.Attachment;
import com.x.processplatform.core.entity.content.Data;
import com.x.processplatform.core.entity.content.ProcessingType;
import com.x.processplatform.core.entity.content.Read;
import com.x.processplatform.core.entity.content.Task;
import com.x.processplatform.core.entity.content.TaskCompleted;
import com.x.processplatform.core.entity.content.Work;
import com.x.processplatform.core.entity.content.WorkLog;
import com.x.processplatform.core.entity.content.Work_;
import com.x.processplatform.core.entity.element.Activity;
import com.x.processplatform.core.entity.element.ActivityType;
import com.x.processplatform.core.entity.element.Application;
import com.x.processplatform.core.entity.element.Form;
import com.x.processplatform.core.entity.element.Manual;
import com.x.processplatform.core.entity.element.Process;
import com.x.query.core.entity.Item;
import com.x.query.core.entity.Item_;

abstract class BaseAction2 extends StandardJaxrsAction {

	private static Logger logger = LoggerFactory.getLogger(BaseAction2.class);

	static DataItemConverter<Item> itemConverter = new DataItemConverter<>(Item.class);

	String getApplicationName(Business business, EffectivePerson effectivePerson, String id) throws Exception {
		Application application = business.application().pick(id);
		if (null != application) {
			return application.getName();
		}
		EntityManagerContainer emc = business.entityManagerContainer();
		EntityManager em = emc.get(Work.class);
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Work> root = cq.from(Work.class);
		Predicate p = cb.equal(root.get(Work_.creatorPerson), effectivePerson.getDistinguishedName());
		p = cb.and(p, cb.equal(root.get(Work_.application), id));
		cq.select(root.get(Work_.applicationName)).where(p);
		List<String> list = em.createQuery(cq).setMaxResults(1).getResultList();
		if (!list.isEmpty()) {
			return list.get(0);
		}
		return null;
	}

	String getProcessName(Business business, EffectivePerson effectivePerson, String id) throws Exception {
		Process process = business.process().pick(id);
		if (null != process) {
			return process.getName();
		}
		EntityManagerContainer emc = business.entityManagerContainer();
		EntityManager em = emc.get(Work.class);
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> cq = cb.createQuery(String.class);
		Root<Work> root = cq.from(Work.class);
		Predicate p = cb.equal(root.get(Work_.creatorPerson), effectivePerson.getDistinguishedName());
		p = cb.and(p, cb.equal(root.get(Work_.process), id));
		cq.select(root.get(Work_.processName)).where(p);
		List<String> list = em.createQuery(cq).setMaxResults(1).getResultList();
		if (!list.isEmpty()) {
			return list.get(0);
		}
		return null;
	}

	Data loadData(Business business, Work work) throws Exception {
		EntityManager em = business.entityManagerContainer().get(Item.class);
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Item> cq = cb.createQuery(Item.class);
		Root<Item> root = cq.from(Item.class);
		Predicate p = cb.equal(root.get(Item_.bundle), work.getJob());
		p = cb.and(p, cb.equal(root.get(Item_.itemCategory), ItemCategory.pp));
		List<Item> list = em.createQuery(cq.where(p)).getResultList();
		if (list.isEmpty()) {
			return new Data();
		} else {
			JsonElement jsonElement = itemConverter.assemble(list);
			if (jsonElement.isJsonObject()) {
				return gson.fromJson(jsonElement, Data.class);
			} else {
				/* 如果不是Object强制返回一个Map对象 */
				return new Data();
			}
		}
	}

	public static class AbstractWo extends GsonPropertyObject {

		@FieldDescribe("活动节点")
		private WoActivity activity;

		@FieldDescribe("工作")
		private WoWork work;

		@FieldDescribe("工作日志对象")
		private List<WoWorkLog> workLogList = new ArrayList<>();

		@FieldDescribe("业务数据")
		private Data data;

		@FieldDescribe("附件对象")
		private List<WoAttachment> attachmentList = new ArrayList<>();

		@FieldDescribe("待办对象")
		private List<WoTask> taskList = new ArrayList<>();

		@FieldDescribe("待阅对象")
		private List<WoRead> readList = new ArrayList<>();

		@FieldDescribe("当前待办索引")
		private Integer currentTaskIndex = -1;

		@FieldDescribe("当前待阅索引")
		private Integer currentReadIndex = -1;

		@FieldDescribe("权限对象")
		private WoControl control;

		@FieldDescribe("表单对象")
		private WoForm form;

		public WoActivity getActivity() {
			return activity;
		}

		public void setActivity(WoActivity activity) {
			this.activity = activity;
		}

		public Data getData() {
			return data;
		}

		public void setData(Data data) {
			this.data = data;
		}

		public Integer getCurrentTaskIndex() {
			return currentTaskIndex;
		}

		public void setCurrentTaskIndex(Integer currentTaskIndex) {
			this.currentTaskIndex = currentTaskIndex;
		}

		public Integer getCurrentReadIndex() {
			return currentReadIndex;
		}

		public void setCurrentReadIndex(Integer currentReadIndex) {
			this.currentReadIndex = currentReadIndex;
		}

		public WoWork getWork() {
			return work;
		}

		public void setWork(WoWork work) {
			this.work = work;
		}

		public List<WoWorkLog> getWorkLogList() {
			return workLogList;
		}

		public void setWorkLogList(List<WoWorkLog> workLogList) {
			this.workLogList = workLogList;
		}

		public List<WoTask> getTaskList() {
			return taskList;
		}

		public void setTaskList(List<WoTask> taskList) {
			this.taskList = taskList;
		}

		public List<WoAttachment> getAttachmentList() {
			return attachmentList;
		}

		public void setAttachmentList(List<WoAttachment> attachmentList) {
			this.attachmentList = attachmentList;
		}

		public List<WoRead> getReadList() {
			return readList;
		}

		public void setReadList(List<WoRead> readList) {
			this.readList = readList;
		}

		public WoControl getControl() {
			return control;
		}

		public void setControl(WoControl control) {
			this.control = control;
		}

		public WoForm getForm() {
			return form;
		}

		public void setForm(WoForm form) {
			this.form = form;
		}

	}

	public static class WoActivity extends GsonPropertyObject {

		private String id;

		private String name;

		private String description;

		private String alias;

		private String position;

		private ActivityType activityType;

		private String resetRange;

		private Integer resetCount;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public String getAlias() {
			return alias;
		}

		public void setAlias(String alias) {
			this.alias = alias;
		}

		public String getPosition() {
			return position;
		}

		public void setPosition(String position) {
			this.position = position;
		}

		public ActivityType getActivityType() {
			return activityType;
		}

		public void setActivityType(ActivityType activityType) {
			this.activityType = activityType;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Integer getResetCount() {
			return resetCount;
		}

		public void setResetCount(Integer resetCount) {
			this.resetCount = resetCount;
		}

		public String getResetRange() {
			return resetRange;
		}

		public void setResetRange(String resetRange) {
			this.resetRange = resetRange;
		}

	}

	public static class WoWork extends Work {

		private static final long serialVersionUID = 3269592171662996253L;

		static WrapCopier<Work, WoWork> copier = WrapCopierFactory.wo(Work.class, WoWork.class, null,
				JpaObject.FieldsInvisible);
	}

	public static class WoWorkLog extends WorkLog {

		private static final long serialVersionUID = 1307569946729101786L;

		public static WrapCopier<WorkLog, WoWorkLog> copier = WrapCopierFactory.wo(WorkLog.class, WoWorkLog.class, null,
				JpaObject.FieldsInvisible);

		private List<WoTaskCompleted> taskCompletedList;

		private List<WoTask> taskList;

		private Integer currentTaskIndex;

		public Integer getCurrentTaskIndex() {
			return currentTaskIndex;
		}

		public void setCurrentTaskIndex(Integer currentTaskIndex) {
			this.currentTaskIndex = currentTaskIndex;
		}

		public List<WoTaskCompleted> getTaskCompletedList() {
			return taskCompletedList;
		}

		public void setTaskCompletedList(List<WoTaskCompleted> taskCompletedList) {
			this.taskCompletedList = taskCompletedList;
		}

		public List<WoTask> getTaskList() {
			return taskList;
		}

		public void setTaskList(List<WoTask> taskList) {
			this.taskList = taskList;
		}

	}

	public static class WoTask extends Task {

		private static final long serialVersionUID = 2279846765261247910L;

		public static WrapCopier<Task, WoTask> copier = WrapCopierFactory.wo(Task.class, WoTask.class, null,
				ListTools.toList(JpaObject.FieldsInvisible, Task.opinionLob_FIELDNAME));

		public void setOpinion(String opinion) {
			this.opinion = opinion;
		}

	}

	public static class WoRead extends Read {

		private static final long serialVersionUID = -8067704098385000667L;

		public static WrapCopier<Read, WoRead> copier = WrapCopierFactory.wo(Read.class, WoRead.class, null,
				ListTools.toList(JpaObject.FieldsInvisible, Read.opinionLob_FIELDNAME));

		public void setOpinion(String opinion) {
			this.opinion = opinion;
		}

	}

	public static class WoTaskCompleted extends TaskCompleted {

		private static final long serialVersionUID = -7253999118308715077L;

		public static WrapCopier<TaskCompleted, WoTaskCompleted> copier = WrapCopierFactory.wo(TaskCompleted.class,
				WoTaskCompleted.class, null,
				ListTools.toList(JpaObject.FieldsInvisible, TaskCompleted.opinionLob_FIELDNAME));
		private Long rank;

		private WorkControl control;

		public void setOpinion(String opinion) {
			this.opinion = opinion;
		}

		public Long getRank() {
			return rank;
		}

		public void setRank(Long rank) {
			this.rank = rank;
		}

		public WorkControl getControl() {
			return control;
		}

		public void setControl(WorkControl control) {
			this.control = control;
		}

	}

	public static class WoAttachment extends Attachment {

		private static final long serialVersionUID = 1954637399762611493L;

		public static List<String> Excludes = new ArrayList<>(JpaObject.FieldsInvisible);

		public static WrapCopier<Attachment, WoAttachment> copier = WrapCopierFactory.wo(Attachment.class,
				WoAttachment.class, null, WoAttachment.Excludes);

		private Long rank;

		private Long referencedCount;

		public Long getRank() {
			return rank;
		}

		public void setRank(Long rank) {
			this.rank = rank;
		}

		public Long getReferencedCount() {
			return referencedCount;
		}

		public void setReferencedCount(Long referencedCount) {
			this.referencedCount = referencedCount;
		}

	}

	public static class WoControl extends GsonPropertyObject {
		/* 是否可以看到 */
		private Boolean allowVisit;
		/* 是否可以直接流转 */
		private Boolean allowProcessing;
		/* 是否可以处理待阅 */
		private Boolean allowReadProcessing;
		/* 是否可以保存数据 */
		private Boolean allowSave;
		/* 是否可以重置处理人 */
		private Boolean allowReset;
		/* 是否可以待阅处理人 */
		private Boolean allowReadReset;
		/* 是否可以召回 */
		private Boolean allowRetract;
		/* 是否可以调度 */
		private Boolean allowReroute;
		/* 是否可以删除 */
		private Boolean allowDelete;

		public Boolean getAllowSave() {
			return allowSave;
		}

		public void setAllowSave(Boolean allowSave) {
			this.allowSave = allowSave;
		}

		public Boolean getAllowReset() {
			return allowReset;
		}

		public void setAllowReset(Boolean allowReset) {
			this.allowReset = allowReset;
		}

		public Boolean getAllowRetract() {
			return allowRetract;
		}

		public void setAllowRetract(Boolean allowRetract) {
			this.allowRetract = allowRetract;
		}

		public Boolean getAllowReroute() {
			return allowReroute;
		}

		public void setAllowReroute(Boolean allowReroute) {
			this.allowReroute = allowReroute;
		}

		public Boolean getAllowProcessing() {
			return allowProcessing;
		}

		public void setAllowProcessing(Boolean allowProcessing) {
			this.allowProcessing = allowProcessing;
		}

		public Boolean getAllowDelete() {
			return allowDelete;
		}

		public void setAllowDelete(Boolean allowDelete) {
			this.allowDelete = allowDelete;
		}

		public Boolean getAllowVisit() {
			return allowVisit;
		}

		public void setAllowVisit(Boolean allowVisit) {
			this.allowVisit = allowVisit;
		}

		public Boolean getAllowReadProcessing() {
			return allowReadProcessing;
		}

		public void setAllowReadProcessing(Boolean allowReadProcessing) {
			this.allowReadProcessing = allowReadProcessing;
		}

		public Boolean getAllowReadReset() {
			return allowReadReset;
		}

		public void setAllowReadReset(Boolean allowReadReset) {
			this.allowReadReset = allowReadReset;
		}

	}

	public class WoForm extends Form {

		private static final long serialVersionUID = 8714459358196550018L;

	}

	protected <T extends AbstractWo> T get(Business business, EffectivePerson effectivePerson, Work work, Class<T> cls)
			throws Exception {
		T t = cls.newInstance();
		
		
		t.setWork(WoWork.copier.copy(work));
		t.setWorkLogList(this.referenceWorkLog(business, work));
		t.setData(this.loadData(business, work));
		t.setAttachmentList(this.listAttachment(business, work));
		this.referenceActivityTaskReadControl(business, effectivePerson, work, t);
		return t;
	}

	private List<WoAttachment> listAttachment(Business business, Work work) throws Exception {
		List<WoAttachment> list = new ArrayList<>();
		List<Attachment> attachments = business.attachment().listWithJobObject(work.getJob());
		list = WoAttachment.copier.copy(attachments);
		// for (WoAttachment o : list) {
		// o.setReferencedCount(business.work().countWithAttachment(o.getId()));
		// }
		SortTools.asc(list, false, "createTime");
		return list;
	}

	private List<WoWorkLog> referenceWorkLog(Business business, Work work) throws Exception {
		List<WoWorkLog> os = WoWorkLog.copier.copy(business.workLog().listWithJobObject(work.getJob()));
		List<WoTaskCompleted> _taskCompleteds = WoTaskCompleted.copier
				.copy(business.taskCompleted().listWithJobObject(work.getJob()));
		List<WoTask> _tasks = WoTask.copier.copy(business.task().listWithJobObject(work.getJob()));
		os = business.workLog().sort(os);

		Map<String, List<WoTaskCompleted>> _map_taskCompleteds = _taskCompleteds.stream()
				.collect(Collectors.groupingBy(o -> o.getActivityToken()));

		Map<String, List<WoTask>> _map_tasks = _tasks.stream()
				.collect(Collectors.groupingBy(o -> o.getActivityToken()));

		for (WoWorkLog o : os) {
			List<WoTaskCompleted> _parts_taskCompleted = _map_taskCompleteds.get(o.getFromActivityToken());
			o.setTaskCompletedList(new ArrayList<WoTaskCompleted>());
			if (!ListTools.isEmpty(_parts_taskCompleted)) {
				for (WoTaskCompleted _taskCompleted : business.taskCompleted().sort(_parts_taskCompleted)) {
					o.getTaskCompletedList().add(_taskCompleted);
					if (_taskCompleted.getProcessingType().equals(ProcessingType.retract)) {
						TaskCompleted _retract = new TaskCompleted();
						_taskCompleted.copyTo(_retract);
						_retract.setRouteName("撤回");
						_retract.setOpinion("撤回");
						_retract.setStartTime(_retract.getRetractTime());
						_retract.setCompletedTime(_retract.getRetractTime());
						o.getTaskCompletedList().add(WoTaskCompleted.copier.copy(_retract));
					}
				}
			}
			List<WoTask> _parts_tasks = _map_tasks.get(o.getFromActivityToken());
			o.setTaskList(new ArrayList<WoTask>());
			if (!ListTools.isEmpty(_parts_tasks)) {
				o.setTaskList(business.task().sort(_parts_tasks));
			}
		}
		return os;
	}

	private void referenceActivityTaskReadControl(Business business, EffectivePerson effectivePerson, Work work,
			AbstractWo wo) throws Exception {
		Activity activity = business.getActivity(work);
		if (null != activity) {
			/** 这里由于Activity是个抽象类,没有具体的Field字段,所以无法用WrapCoiper进行拷贝 */
			WoActivity woActivity = new WoActivity();
			activity.copyTo(woActivity);
			wo.setActivity(woActivity);
		}
		List<Task> taskList = business.task().listWithWorkObject(work);
		SortTools.asc(taskList, "startTime");
		Task task = null;
		for (int i = 0; i < taskList.size(); i++) {
			Task o = taskList.get(i);
			if (StringUtils.equals(o.getPerson(), effectivePerson.getDistinguishedName())) {
				task = o;
				wo.setCurrentTaskIndex(i);
				break;
			}
		}
		wo.setTaskList(WoTask.copier.copy(taskList));
		List<Read> readList = business.read().listWithWorkObject(work);
		SortTools.asc(readList, "startTime");
		Read read = null;
		for (int i = 0; i < readList.size(); i++) {
			Read o = readList.get(i);
			if (StringUtils.equals(o.getPerson(), effectivePerson.getDistinguishedName())) {
				read = o;
				wo.setCurrentReadIndex(i);
				break;
			}
		}
		wo.setReadList(WoRead.copier.copy(readList));
		Application application = business.application().pick(work.getApplication());
		Process process = business.process().pick(work.getProcess());
		Long taskCompletedCount = business.taskCompleted()
				.countWithPersonWithWork(effectivePerson.getDistinguishedName(), work);
		Long readCompletedCount = business.readCompleted()
				.countWithPersonWithWork(effectivePerson.getDistinguishedName(), work);
		Long reviewCount = business.review().countWithPersonWithWork(effectivePerson.getDistinguishedName(), work);
		WoControl control = new WoControl();
		/** 工作是否可以打开(管理员 或 有task,taskCompleted,read,readCompleted,review的人) */
		control.setAllowVisit(false);
		/** 工作是否可以流转(有task的人) */
		control.setAllowProcessing(false);
		/** 工作是否可以处理待阅(有read的人) */
		control.setAllowReadProcessing(false);
		/** 工作是否可保存(管理员 或者 有本人的task) */
		control.setAllowSave(false);
		/** 工作是否可重置(有本人待办 并且 活动设置允许重置 */
		control.setAllowReset(false);
		/** 工作是否可以撤回(当前人是上一个处理人 并且 还没有其他人处理过) */
		control.setAllowRetract(false);
		/** 工作是否可调度(管理员 并且 此活动在流程设计中允许调度) */
		control.setAllowReroute(false);
		/** 工作是否可删除(管理员 或者 此活动在流程设计中允许删除且当前待办人是文件的创建者) */
		control.setAllowDelete(false);
		/** 设置allowVisit */
		if ((null != task) || (null != read) || (taskCompletedCount > 0) || (readCompletedCount > 0)
				|| (reviewCount > 0)) {
			control.setAllowVisit(true);
		} else if (effectivePerson.isUser(work.getCreatorPerson())) {
			control.setAllowVisit(true);
		} else if (business.canManageApplicationOrProcess(effectivePerson, application, process)) {
			control.setAllowVisit(true);
		}
		/** 设置allowProcessing */
		if (null != task) {
			control.setAllowProcessing(true);
		}
		/** 设置allowReadProcessing */
		if (null != read) {
			control.setAllowReadProcessing(true);
		}
		/** 设置 allowSave */
		if (null != task) {
			control.setAllowSave(true);
		} else if (business.canManageApplicationOrProcess(effectivePerson, application, process)) {
			control.setAllowSave(true);
		}
		/** 设置 allowReset */
		if (Objects.equals(activity.getActivityType(), ActivityType.manual)
				&& BooleanUtils.isTrue(((Manual) activity).getAllowReset()) && null != task) {
			control.setAllowReset(true);
		}
		/** 设置 allowRetract */
		if (Objects.equals(activity.getActivityType(), ActivityType.manual)
				&& BooleanUtils.isTrue(((Manual) activity).getAllowRetract())) {
			/** 标志文件还没有处理过 */
			if (0 == business.taskCompleted().countWithPersonWithActivityToken(effectivePerson.getDistinguishedName(),
					work.getActivityToken())) {
				/** 找到到达当前活动的workLog */
				WorkLog workLog = business.workLog().getWithArrivedActivityTokenObject(work.getActivityToken());
				if (null != workLog) {
					/** 查找上一个环节的已办,如果只有一个,且正好是当前人的,那么可以召回 */
					List<TaskCompleted> taskCompletedList = business.taskCompleted()
							.listWithActivityTokenObject(workLog.getFromActivityToken());
					if (taskCompletedList.size() == 1 && StringUtils.equals(effectivePerson.getDistinguishedName(),
							taskCompletedList.get(0).getPerson())) {
						control.setAllowRetract(true);
					}
				}
			}
		}
		/** 设置 allowReroute */
		if (effectivePerson.isManager()) {
			/** 管理员可以调度 */
			control.setAllowReroute(true);
		} else if (business.organization().person().hasRole(effectivePerson,
				OrganizationDefinition.ProcessPlatformManager)) {
			/** 有流程管理角色的可以 */
			control.setAllowReroute(true);
		} else if (BooleanUtils.isTrue(activity.getAllowReroute())) {
			/** 如果活动设置了可以调度 */
			if ((null != process) && effectivePerson.isUser(process.getControllerList())) {
				/** 如果是流程的管理员那么可以调度 */
				control.setAllowReroute(true);
			} else if ((null != application) && effectivePerson.isUser(application.getControllerList())) {
				/** 如果是应用的管理员那么可以调度 */
				control.setAllowReroute(true);
			}
		}
		/* 设置 allowDelete */
		if (business.canManageApplicationOrProcess(effectivePerson, application, process)) {
			control.setAllowDelete(true);
		} else if (Objects.equals(activity.getActivityType(), ActivityType.manual)
				&& BooleanUtils.isTrue(((Manual) activity).getAllowDeleteWork())) {
			if (null != task && StringUtils.equals(work.getCreatorPerson(), effectivePerson.getDistinguishedName())) {
				control.setAllowDelete(true);
			}
		}
		wo.setControl(control);
	}
}
