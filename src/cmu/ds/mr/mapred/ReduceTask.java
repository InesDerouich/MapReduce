package cmu.ds.mr.mapred;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cmu.ds.mr.conf.JobConf;
import cmu.ds.mr.io.FileSplit;
import cmu.ds.mr.io.LineRecordReader;
import cmu.ds.mr.io.MapOutputCollector;
import cmu.ds.mr.util.Util;

public class ReduceTask extends Task {
  
  public ReduceTask(TaskID taskid, JobConf taskconf, TaskStatus taskStatus){
    super(taskid, taskconf, taskStatus);
  }

  @Override
  public void startTask(JobConf jobConf, TaskUmbilicalProtocol taskTrackerProxy) throws IOException,
          ClassNotFoundException, InterruptedException, RuntimeException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    

  }

  @Override
  public TaskRunner createRunner(TaskTracker tracker, Task task) throws IOException {
    // TODO Auto-generated method stub
    return null;
  }
}
