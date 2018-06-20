package org.thoughtcrime.securesms.jobmanager.requirements;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.dependencies.ContextDependent;

import java.util.concurrent.TimeUnit;

/**
 * Uses exponential backoff to re-schedule jobs to be retried in the future.
 */
public class BackoffRequirement implements Requirement, ContextDependent {

  private static final long MAX_WAIT = TimeUnit.HOURS.toMillis(1);

  private transient Context context;

  public BackoffRequirement(@NonNull Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public boolean isPresent(@NonNull Job job) {
    return System.currentTimeMillis() >= calculateNextRunTime(job);
  }

  @Override
  public void onRetry(@NonNull Job job) {
    long nextRunTime = BackoffRequirement.calculateNextRunTime(job);
    BackoffReceiver.setUniqueAlarm(context, nextRunTime);
  }

  @Override
  public void setContext(Context context) {
    this.context = context.getApplicationContext();
  }

  private static long calculateNextRunTime(@NonNull Job job) {
    int  tryCount     = job.getRunIteration();
    long lastRunTime  = job.getLastRunTime();

    long targetTime   = lastRunTime + (long) (Math.pow(2, tryCount - 1) * 1000);
    long furthestTime = System.currentTimeMillis() + MAX_WAIT;
    long boundTime    = System.currentTimeMillis() + job.getRetryDuration();

    return Math.min(targetTime, Math.min(furthestTime, boundTime));
  }
}
