// Put thread group's name into props
String threadGroupName = org.apache.jmeter.threads.JMeterContextService
    .getContext()
    .getThreadGroup()
    .getName();
props.put("AgentThreadGroupName", threadGroupName);

// Put number of currently active threads in the group
Integer activeThreads = ctx.getThreadGroup().getNumberOfThreads();
props.put("AgentActiveThreads", activeThreads);

Integer agentActive = 0;
Integer concurrentAgentNumber = Integer.valueOf(args[0]); // Total number of Agents in the test
Integer threadNum = (int) ctx.getThreadNum() + 1; // Current thread number in the group
Integer iteration = vars.getIteration() as Integer; // Current iteration

if (threadNum == 1 && iteration == 1) {
    // Force the inactivity of all agents after threads init
    for (int i = 1; i <= concurrentAgentNumber; i++) {
        props.put("activityAgent" + i, agentActive);
    }
} else {
    // Force the inactivity of current agent
    props.put("activityAgent" + threadNum, agentActive);
}

// Set a Max Load timing when number of current agent thread is equal to the total number
if (threadNum == concurrentAgentNumber) {
    Integer time_maxload_s = (Integer) (new Date().getTime() / 1000L);
    props.put("Test Max load", time_maxload_s);
}
SampleResult.setIgnore();