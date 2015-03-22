package main

import (
	"flag"
	"fmt"
	"time"
	"log/syslog"
	exec "github.com/mesos/mesos-go/executor"
	mesos "github.com/mesos/mesos-go/mesosproto"
)

type bundesExecutor struct {
	logger *syslog.Writer
	tasksLaunched int
}

func newBundesExecutor(logger *syslog.Writer) (*bundesExecutor) {
	return &bundesExecutor{tasksLaunched: 0, logger: logger}
}

func (exec *bundesExecutor) Registered(driver exec.ExecutorDriver, execInfo *mesos.ExecutorInfo, fwinfo *mesos.FrameworkInfo, slaveInfo *mesos.SlaveInfo) {
	exec.logger.Info(fmt.Sprint("registered executor on slave: ", slaveInfo.GetHostname()))
}

func (exec *bundesExecutor) Reregistered(driver exec.ExecutorDriver, slaveInfo *mesos.SlaveInfo) {
	exec.logger.Info(fmt.Sprint("re-registered executor on slave: ", slaveInfo.GetHostname()))
}

func (exec *bundesExecutor) Disconnected(exec.ExecutorDriver) {
	exec.logger.Info(fmt.Sprint("executor disconnected."))
}

func (exec *bundesExecutor) LaunchTask(driver exec.ExecutorDriver, taskInfo *mesos.TaskInfo) {
	exec.logger.Info(fmt.Sprint("lauching task: ", taskInfo.GetName(), "with command: ", taskInfo.Command.GetValue()))

	runStatus := &mesos.TaskStatus{
		TaskId: taskInfo.GetTaskId(),
		State:  mesos.TaskState_TASK_RUNNING.Enum(),
	}
	_, err := driver.SendStatusUpdate(runStatus)


	if err != nil {
		exec.logger.Warning(fmt.Sprint("got error: ", err))
	}

	exec.tasksLaunched++
	exec.logger.Info(fmt.Sprint("total tasks launched: ", exec.tasksLaunched))


	time.Sleep(10 * time.Second)

	exec.logger.Info(fmt.Sprint("finishing task: ", taskInfo.GetName()))

	finStatus := &mesos.TaskStatus{
		TaskId: taskInfo.GetTaskId(),
		State:  mesos.TaskState_TASK_FINISHED.Enum(),
	}
	_, err = driver.SendStatusUpdate(finStatus)
	if err != nil {
		exec.logger.Warning(fmt.Sprint("got error: ", err))
	}
	exec.logger.Info(fmt.Sprint("task finished: ", taskInfo.GetName()))
}

func (exec *bundesExecutor) KillTask(exec.ExecutorDriver, *mesos.TaskID) {
	exec.logger.Info("kill task")
}

func (exec *bundesExecutor) FrameworkMessage(driver exec.ExecutorDriver, msg string) {
	exec.logger.Info(fmt.Sprint("got framework message: ", msg))
}

func (exec *bundesExecutor) Shutdown(exec.ExecutorDriver) {
	exec.logger.Info("shutting down the executor")
}

func (exec *bundesExecutor) Error(driver exec.ExecutorDriver, err string) {
	exec.logger.Warning(fmt.Sprint("got error message: ", err))
}

// -------------------------- func inits () ----------------- //
func init() {
	flag.Parse()
}

func main() {

	logger, err := syslog.New(syslog.LOG_INFO|syslog.LOG_DAEMON, "bundes")
	if (err != nil) {
		fmt.Println("Could not create logger, exiting.")
		return
	}

	logger.Info("Starting Bundesrat Executor (Go)")

	driver, err := exec.NewMesosExecutorDriver(newBundesExecutor(logger))
	if err != nil {
		logger.Err(fmt.Sprint("unable to create a executor driver:", err.Error()))
		return
	}

	_, err = driver.Start()
	if err != nil {
		logger.Err(fmt.Sprint("could not start executor driver: ", err))
		return
	}
	logger.Info("executor started and running")
	driver.Join()
}
