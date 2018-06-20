/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;
import android.util.Log;

import org.thoughtcrime.securesms.jobmanager.persistence.PersistentStorage;

import java.io.IOException;

class JobConsumer extends Thread {

  private static final String TAG = JobConsumer.class.getSimpleName();

  enum JobResult {
    SUCCESS,
    FAILURE,
    DEFERRED
  }

  private final JobQueue          jobQueue;
  private final PersistentStorage persistentStorage;

  public JobConsumer(String name, JobQueue jobQueue, PersistentStorage persistentStorage) {
    super(name);
    this.jobQueue          = jobQueue;
    this.persistentStorage = persistentStorage;
  }

  @Override
  public void run() {
    while (true) {
      Job       job    = jobQueue.getNext();
      JobResult result = runJob(job);

      if (result == JobResult.DEFERRED) {
        jobQueue.push(job);

        if (job.isPersistent()) {
          try {
            persistentStorage.update(job);
          } catch (IOException e) {
            Log.w(TAG, "Failed to update job on persistent storage. Canceling it.", e);
            job.onCanceled();
          }
        }
      } else {
        if (result == JobResult.FAILURE) {
          job.onCanceled();
        }

        if (job.isPersistent()) {
          persistentStorage.remove(job.getPersistentId());
        }

        if (job.getWakeLock() != null && job.getWakeLockTimeout() == 0) {
          job.getWakeLock().release();
        }

        if (job.getGroupId() != null) {
          jobQueue.setGroupIdAvailable(job.getGroupId());
        }
      }
    }
  }

  private JobResult runJob(Job job) {
    while (canRetry(job)) {
      try {
        job.run();
        return JobResult.SUCCESS;
      } catch (Exception exception) {
        Log.w(TAG, exception);
        if (exception instanceof RuntimeException) {
          throw (RuntimeException)exception;
        } else if (!job.onShouldRetry(exception)) {
          return JobResult.FAILURE;
        }

        job.onRetry();
        if (!job.isRequirementsMet()) {
          return JobResult.DEFERRED;
        }
      }
    }

    return JobResult.FAILURE;
  }

  private boolean canRetry(@NonNull Job job) {
    if (job.getRetryCount() > 0) {
      return job.getRunIteration() < job.getRetryCount();
    }
    return job.getStartTime() == 0 || System.currentTimeMillis() < job.getStartTime() + job.getRetryDuration();
  }

}
