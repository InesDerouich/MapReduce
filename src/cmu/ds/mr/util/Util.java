package cmu.ds.mr.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class Util {
  
  public static final long TIME_INTERVAL_MONITOR = 1000;
  public static final String BLOCK_SIZE = "fs.block.size";
  public static final String SYS_ROOT_DIR = "fs.root.dir";
  
  public static final String NUM_MAP_TASK = "mapred.map.tasks";
  public static final String NUM_RED_TASK = "mapred.red.tasks";
  public static final String JOB_NAME = "mapred.job.name";
  
  public static final String SERVICE_NAME = "JobSubmissionProtocol";
  
  public static final String CONFIG_PATH = "./conf/mapred.conf";

  
  
  public static String stringifyException(Throwable e) {
    StringWriter stm = new StringWriter();
    PrintWriter wrt = new PrintWriter(stm);
    e.printStackTrace(wrt);
    wrt.close();
    return stm.toString();
  }

  
  public static Object newInstance(Class<?> theClass) throws RuntimeException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    Constructor<?> constructor = theClass.getConstructor();
    return constructor.newInstance();
  }
  
}
