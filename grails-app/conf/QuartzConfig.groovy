import org.textup.Helpers

//
// NOTE:
//
// if you are changing `ENABLE_QUARTZ` to true on the staging or development environments,
// make sure to double check that Quartz has no triggers that will fire. Triggers that fire
// immediately when past due will cause all those outdated scheduled messages to erroneously
// send to possibly many unintended recipients.
//

def shouldEnable = System.getenv("ENABLE_QUARTZ") ?: System.getProperty("ENABLE_QUARTZ")

quartz {
    autoStartup = Helpers.to(boolean, shouldEnable, false)
    jdbcStore = false
    waitForJobsToCompleteOnShutdown = true
    exposeSchedulerInRepository = false

    props {
        scheduler.skipUpdateCheck = true
    }
}

environments {
    test {
        quartz {
            autoStartup = false
        }
    }
    production {
        quartz {
            jdbcStore = true

            props {
                threadPool.'class' = 'org.quartz.simpl.SimpleThreadPool'
                threadPool.threadCount = 10

                jobStore.'class' = 'org.quartz.impl.jdbcjobstore.JobStoreTX'
                jobStore.driverDelegateClass = 'org.quartz.impl.jdbcjobstore.StdJDBCDelegate'

                jobStore.useProperties = false
                jobStore.tablePrefix = 'QRTZ_'

                plugin.shutdownhook.'class' = 'org.quartz.plugins.management.ShutdownHookPlugin'
                plugin.shutdownhook.cleanShutdown = true
            }
        }
    }
}
