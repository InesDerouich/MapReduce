package cmu.ds.mr.mapred;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cmu.ds.mr.conf.JobConf;
import cmu.ds.mr.mapred.JobStatus.JobState;
import cmu.ds.mr.mapred.TaskStatus.TaskState;
import cmu.ds.mr.mapred.TaskStatus.TaskType;
import cmu.ds.mr.util.Log;
import cmu.ds.mr.util.Util;

public class JobTracker implements JobSubmissionProtocol, InterTrackerProtocol {
	private static final Log LOG = new Log("JobTracker.class");
	// public static enum State { INITIALIZING, RUNNING }
	// State state = State.INITIALIZING;

	private static Queue<JobInProgress> jobQueue = new LinkedList<JobInProgress>();
	private static Map<JobID, JobInProgress> jobTable = new TreeMap<JobID, JobInProgress>();
	private static Map<String, Set<TaskStatus>> tasktrackers = new TreeMap<String, Set<TaskStatus>>();
	private static Map<String, Integer> validtasktrackers = new TreeMap<String, Integer>();
	private static TaskScheduler taskscheduler = new TaskScheduler(jobQueue,
			jobTable);
	private String jobIdentifier;
	// private final TaskScheduler taskScheduler = new TaskScheduler();

	private int nextID = 1;
	private int nextTasktracker = 1;
	int totalSubmissions = 0;

	public JobTracker() {
		super();
		// this.jobIdentifier = "jobtracker";
	}

	// public JobState submitJob(int JobID){
	// if(jobQueue.contains(JobID)){
	// return null;
	// }
	// return null;
	// }

	@Override
	public JobID getNewJobId() throws IOException {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		String curDate = dateFormat.format(new Date());
		return new JobID(curDate, nextID++);
	}

	@Override
	public synchronized JobStatus submitJob(JobID jobid, JobConf jobConf)
			throws IOException {

		// check if job already running, don't start twice
		if (jobTable.containsKey(jobid)) {
			return jobTable.get(jobid).getStatus();
		}

		JobInProgress job = new JobInProgress(jobid, this, jobConf);

		// TODO: need to check Queue later
		if (!jobQueue.offer(job)) {
			// LOG.info("submitJob: Cannot enqueue the job");
			return null;
		}

		return addJob(jobid, job);

	}

	private synchronized JobStatus addJob(JobID jobId, JobInProgress job) {
		totalSubmissions++;

		synchronized (jobTable) {
			jobTable.put(jobId, job);
			job.getStatus().setState(JobState.RUNNING);
			// LOG.info("addJob(): finish adding job #" +
			// job.getJobid().getId());
		}
		return job.getStatus();
	}

	@Override
	public void killJob(JobID jobid) throws IOException {
		// TODO Auto-generated method stub
		if (null == jobid) {
			// LOG.info("Null jobid object sent to JobTracker.killJob()");
			return;
		}

		JobInProgress job = jobTable.get(jobid);

		if (null == job) {
			// LOG.info("killJob(): JobId " + jobid.toString() +
			// " is not a valid job");
			return;
		}

		job.kill();
	}

	@Override
	public JobStatus getJobStatus(JobID jobid) throws IOException {
		if (null == jobid) {
			// LOG.warn("JobTracker.getJobStatus() cannot get status for null jobid");
			return null;
		}
		synchronized (this) {
			JobInProgress job = jobTable.get(jobid);
			if (job == null) {
				// LOG.warn("JobTracker.getJobStatus() cannot get job from the given jobid");
			}
			return job.getStatus();
		}
	}

	private synchronized JobStatus[] getJobStatus(
			Collection<JobInProgress> jips, boolean toComplete) {
		if (jips == null || jips.isEmpty()) {
			return new JobStatus[] {};
		}
		ArrayList<JobStatus> jobStatusList = new ArrayList<JobStatus>();
		for (JobInProgress jip : jips) {
			JobStatus status = jip.getStatus();
			status.setStartTime(jip.getStartTime());
			if (toComplete) {
				if (status.getState() == JobState.RUNNING)
					jobStatusList.add(status);
			} else {
				jobStatusList.add(status);
			}
		}
		return (JobStatus[]) jobStatusList.toArray(new JobStatus[jobStatusList
				.size()]);
	}

	@Override
	public JobStatus[] jobsToComplete() throws IOException {
		return getJobStatus(jobTable.values(), true);
	}

	@Override
	public JobStatus[] getAllJobs() throws IOException {
		return getJobStatus(jobTable.values(), false);
	}

	@Override
	public String getSystemDir() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Task heartbeat(TaskTrackerStatus status) throws IOException {
		// TODO Auto-generated method stub
		String tasktrackername = status.getTaskTrackername();
		synchronized (jobTable) {
			synchronized (tasktrackers) {
				synchronized (validtasktrackers) {
					if (!validtasktrackers.containsKey(tasktrackername)) {
						// initial heartbeat, then create a tasklist for this
						// tracker;
						LOG.info("recv new TaskTracker:" + tasktrackername);
						Set<TaskStatus> tasklist = new HashSet<TaskStatus>();
						tasktrackers.put(tasktrackername, tasklist);
						validtasktrackers.put(tasktrackername, 0);

						// return a special task with trynum = -1 to notify the
						// tracker clean its tasklist;
						TaskID tid = new TaskID(null, TaskType.MAP, 0, -1);
						return new MapTask(tid, null, new TaskStatus(tid,
								TaskState.DEFINE, TaskType.MAP));
					}

					// anytime receive the heartbeat from the tracker, reset its
					// timeout counter.
					validtasktrackers.put(tasktrackername, 0);
				}

				List<TaskStatus> tasks = status.getTaskStatusList();
				LOG.debug("size of incoming tasklist:" + tasks.size());
				Set<TaskStatus> tl = tasktrackers.get(tasktrackername);
				LOG.debug("finish the tasktrackers.get(tasktrackername)");
				
				List<TaskStatus> sucorfail = new ArrayList<TaskStatus>();
				for (TaskStatus taskstatus : tasks) {
					JobID jid = taskstatus.getTaskId().getJobId();
					TaskState tstate = taskstatus.getState();
					TaskType ttype = taskstatus.getType();
					if (tstate == TaskState.SUCCEEDED) {

						// if the task has been finished, remove the task from
						// that tracker's record;
						for (TaskStatus ts : tl) {
							if (ts.getTaskId().toString()
									.equals(taskstatus.getTaskId().toString())) {
								ts.setState(TaskState.SUCCEEDED);
								sucorfail.add(ts);
							}
						}

						if (ttype == TaskType.MAP) {
							JobInProgress currentjob = jobTable.get(jid);
							float currentprogress = currentjob
									.getStatus().getMapProgress();
							int num = currentjob.getJobconf()
									.getNumMapTasks();
							currentjob
									.getStatus()
									.setMapProgress(
											currentprogress + 1 / (float) num);
							
						} else if (ttype == TaskType.REDUCE) {
							JobInProgress currentjob = jobTable.get(jid);
							float currentprogress = currentjob
									.getStatus().getReduceProgress();
							int num = currentjob.getJobconf()
									.getNumReduceTasks();
							currentjob
									.getStatus()
									.setReduceProgress(
											currentprogress + 1 / (float) num);
							if(currentjob.getStatus().getReduceProgress() > 0.999){
								currentjob.getStatus().setState(JobState.SUCCEEDED);
							}
						}
					}
					if (tstate == TaskState.FAILED) {
						// if the task failed, remove the task from
						// that tracker's record and reassign it
						for (TaskStatus ts : tl) {
							if (ts.getTaskId().toString()
									.equals(taskstatus.getTaskId().toString())) {
								ts.setState(TaskState.FAILED);
								sucorfail.add(ts);
							}
						}

						LOG.info("task:" + tasks.toString() + "failed");
						taskscheduler.recoverFailedTask(taskstatus);

					}
				}

				// remove all success or fail tasks from local record.
				for (TaskStatus ts : sucorfail) {
					tl.remove(ts);
				}

				// assign new task if there is one
				if (status.getNumFreeSlots() > 0) {
					Task newtask = taskscheduler.assignTask();
					// add the new assigned task to local record;
					if(newtask != null)tl.add(newtask.getTaskStatus());
					return newtask;
				}
			}
		}
		return null;
	}

	@Override
	public int getNewTaskTrackerId() throws IOException, RemoteException {
		// TODO Auto-generated method stub
		return nextTasktracker++;
	}

	@Override
	public void reportTaskTrackerError(String taskTracker, String errorClass,
			String errorMessage) throws IOException {
		// TODO Auto-generated method stub

	}

	public static void main(String[] args) {
		LOG.setDebug(false);
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		// LOG.isInfoEnabled();
		// LOG.info("Starting jobtracker");
		try {
			String name = Util.SERVICE_NAME;
			JobTracker jobtracker = new JobTracker();
			JobSubmissionProtocol stub = (JobSubmissionProtocol) UnicastRemoteObject
					.exportObject(jobtracker, 0);
			Registry registry = LocateRegistry.getRegistry();

			registry.rebind(name, stub);

			name = Util.SERVICE_NAME_INTERTRACKER;
			registry.rebind(name, (InterTrackerProtocol) stub);

			System.out.println("SERVICE bound");

			InetAddress addr = InetAddress.getLocalHost();
			String localhostname = addr.getCanonicalHostName();
			System.out.printf("JobTracker host name: %s\n", localhostname);

			// LOG.info("jobtracker bound");
		} catch (Exception e) {
			System.err.println("SERVICE bound exception:");
			e.printStackTrace();
			// LOG.error("JobTracker exception:" + Util.stringifyException(e));
		}

		// timeout controller run at any 5 seconds
		TimeoutController tocontroller = new TimeoutController(tasktrackers,
				taskscheduler, validtasktrackers);
		ScheduledExecutorService schExec = Executors.newScheduledThreadPool(8);
		ScheduledFuture<?> schFuture = schExec.scheduleWithFixedDelay(
				tocontroller, 0, 5, TimeUnit.SECONDS);

	}

}
