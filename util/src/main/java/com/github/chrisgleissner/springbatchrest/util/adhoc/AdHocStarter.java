package com.github.chrisgleissner.springbatchrest.util.adhoc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.JobLocator;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

import static org.springframework.batch.repeat.RepeatStatus.FINISHED;

@Slf4j
@Component
public class AdHocStarter {

    private final JobLocator jobLocator;
    private final JobLauncher jobLauncher;
    private final JobBuilderFactory jobs;
    private final StepBuilderFactory steps;

    public AdHocStarter(JobLocator jobLocator, JobLauncher jobLauncher, JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory) {
        this.jobLocator = jobLocator;
        this.jobLauncher = jobLauncher;
        this.jobs = jobBuilderFactory;
        this.steps = stepBuilderFactory;
    }

    public JobExecution start(String jobName, JobParameters jobParameters) {
        try {
            return start(jobLocator.getJob(jobName), jobParameters);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch job " + jobName, e);
        }
    }

    public JobExecution start(String jobName, Consumer<JobParameters> consumer, JobParameters jobParameters) {
        Job job = jobs.get(jobName)
                .incrementer(new RunIdIncrementer())
                .flow(steps.get("step").allowStartIfComplete(true).tasklet(new ConsumerTaskletAdapter(consumer, jobParameters)).build())
                .end().build();
        return start(job, jobParameters);
    }

    public JobExecution start(Job job, JobParameters jobParameters) {
        try {
            log.info("Starting job {}", job.getName());
            return jobLauncher.run(job, jobParameters);
        } catch (Exception e) {
            throw new RuntimeException("Failed to launch job " + job.getName(), e);
        }
    }

    private class ConsumerTaskletAdapter implements Tasklet {
        private final JobParameters parameters;
        private Consumer<JobParameters> consumer;

        public ConsumerTaskletAdapter(Consumer<JobParameters> consumer, JobParameters parameters) {
            this.consumer = consumer;
            this.parameters = parameters;
        }

        @Override
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
            consumer.accept(parameters);
            return FINISHED;
        }
    }
}