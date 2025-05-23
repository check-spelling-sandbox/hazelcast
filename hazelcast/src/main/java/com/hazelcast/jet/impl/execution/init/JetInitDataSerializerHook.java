/*
 * Copyright (c) 2008-2025, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.impl.execution.init;

import com.hazelcast.internal.serialization.DataSerializerHook;
import com.hazelcast.internal.serialization.impl.FactoryIdHelper;
import com.hazelcast.jet.JobStatusEvent;
import com.hazelcast.jet.impl.JobExecutionRecord;
import com.hazelcast.jet.impl.JobExecutionRecord.SnapshotStats;
import com.hazelcast.jet.impl.JobRecord;
import com.hazelcast.jet.impl.JobRepository.FilterJobResultByNamePredicate;
import com.hazelcast.jet.impl.JobRepository.UpdateJobExecutionRecordEntryProcessor;
import com.hazelcast.jet.impl.JobResult;
import com.hazelcast.jet.impl.JobSummary;
import com.hazelcast.jet.impl.JobSuspensionCauseImpl;
import com.hazelcast.jet.impl.SnapshotValidationRecord;
import com.hazelcast.jet.impl.connector.WriteFileP;
import com.hazelcast.jet.impl.operation.AddJobStatusListenerOperation;
import com.hazelcast.jet.impl.operation.CheckLightJobsOperation;
import com.hazelcast.jet.impl.operation.GetJobAndSqlSummaryListOperation;
import com.hazelcast.jet.impl.operation.GetJobConfigOperation;
import com.hazelcast.jet.impl.operation.GetJobIdsOperation;
import com.hazelcast.jet.impl.operation.GetJobIdsOperation.GetJobIdsResult;
import com.hazelcast.jet.impl.operation.GetJobMetricsOperation;
import com.hazelcast.jet.impl.operation.GetJobStatusOperation;
import com.hazelcast.jet.impl.operation.GetJobSubmissionTimeOperation;
import com.hazelcast.jet.impl.operation.GetJobSummaryListOperation;
import com.hazelcast.jet.impl.operation.GetJobSuspensionCauseOperation;
import com.hazelcast.jet.impl.operation.GetLocalExecutionMetricsOperation;
import com.hazelcast.jet.impl.operation.InitExecutionOperation;
import com.hazelcast.jet.impl.operation.IsJobUserCancelledOperation;
import com.hazelcast.jet.impl.operation.JoinSubmittedJobOperation;
import com.hazelcast.jet.impl.operation.NotifyMemberShutdownOperation;
import com.hazelcast.jet.impl.operation.PrepareForPassiveClusterOperation;
import com.hazelcast.jet.impl.operation.ResumeJobOperation;
import com.hazelcast.jet.impl.operation.SnapshotPhase1Operation;
import com.hazelcast.jet.impl.operation.SnapshotPhase1Operation.SnapshotPhase1Result;
import com.hazelcast.jet.impl.operation.SnapshotPhase2Operation;
import com.hazelcast.jet.impl.operation.StartExecutionOperation;
import com.hazelcast.jet.impl.operation.SubmitJobOperation;
import com.hazelcast.jet.impl.operation.TerminateExecutionOperation;
import com.hazelcast.jet.impl.operation.TerminateJobOperation;
import com.hazelcast.jet.impl.operation.UpdateJobConfigOperation;
import com.hazelcast.jet.impl.operation.UploadJobMetaDataOperation;
import com.hazelcast.jet.impl.operation.UploadJobMultiPartOperation;
import com.hazelcast.jet.impl.processor.NoopP;
import com.hazelcast.jet.impl.processor.ProcessorSupplierFromSimpleSupplier;
import com.hazelcast.jet.impl.processor.SessionWindowP;
import com.hazelcast.jet.impl.processor.SlidingWindowP.SnapshotKey;
import com.hazelcast.jet.impl.util.AsyncSnapshotWriterImpl;
import com.hazelcast.jet.impl.util.WrappingProcessorMetaSupplier;
import com.hazelcast.jet.impl.util.WrappingProcessorSupplier;
import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import static com.hazelcast.jet.impl.JetFactoryIdHelper.JET_IMPL_DS_FACTORY;
import static com.hazelcast.jet.impl.JetFactoryIdHelper.JET_IMPL_DS_FACTORY_ID;

public final class JetInitDataSerializerHook implements DataSerializerHook {

    public static final int EXECUTION_PLAN = 0;
    public static final int VERTEX_DEF = 1;
    public static final int EDGE_DEF = 2;
    public static final int JOB_RECORD = 3;
    public static final int JOB_RESULT = 4;
    public static final int INIT_EXECUTION_OP = 5;
    public static final int START_EXECUTION_OP = 6;
    public static final int JOB_STATUS_EVENT = 7;
    public static final int SUBMIT_JOB_OP = 8;
    public static final int GET_JOB_STATUS_OP = 9;
    public static final int SNAPSHOT_PHASE1_OPERATION = 10;
    public static final int JOB_EXECUTION_RECORD = 11;
    public static final int SESSION_WINDOW_P_WINDOWS = 12;
    public static final int SLIDING_WINDOW_P_SNAPSHOT_KEY = 13;
    public static final int GET_JOB_IDS = 14;
    public static final int JOIN_SUBMITTED_JOB = 15;
    public static final int UPDATE_JOB_EXECUTION_RECORD_EP = 16;
    public static final int TERMINATE_EXECUTION_OP = 17;
    public static final int FILTER_JOB_RESULT_BY_NAME = 18;
    public static final int GET_JOB_IDS_RESULT = 19;
    public static final int GET_JOB_SUBMISSION_TIME_OP = 20;
    public static final int GET_JOB_CONFIG_OP = 21;
    public static final int TERMINATE_JOB_OP = 22;
    public static final int ASYNC_SNAPSHOT_WRITER_SNAPSHOT_DATA_KEY = 23;
    public static final int ASYNC_SNAPSHOT_WRITER_SNAPSHOT_DATA_VALUE_TERMINATOR = 24;
    public static final int SNAPSHOT_PHASE1_RESULT = 25;
    public static final int RESUME_JOB_OP = 26;
    public static final int NOTIFY_MEMBER_SHUTDOWN_OP = 27;
    public static final int GET_JOB_SUMMARY_LIST_OP = 28;
    public static final int JOB_SUMMARY = 29;
    public static final int SNAPSHOT_STATS = 30;
    public static final int PREPARE_FOR_PASSIVE_CLUSTER_OP = 31;
    public static final int SNAPSHOT_VALIDATION_RECORD = 32;
    public static final int ADD_JOB_STATUS_LISTENER_OP = 33;
    public static final int GET_JOB_METRICS_OP = 34;
    public static final int GET_LOCAL_JOB_METRICS_OP = 35;
    public static final int SNAPSHOT_PHASE2_OPERATION = 36;
    public static final int WRITE_FILE_P_FILE_ID = 42;
    public static final int JOB_SUSPENSION_CAUSE = 43;
    public static final int GET_JOB_SUSPENSION_CAUSE_OP = 44;
    public static final int PROCESSOR_SUPPLIER_FROM_SIMPLE_SUPPLIER = 45;
    public static final int NOOP_PROCESSOR_SUPPLIER = 46;
    public static final int CHECK_LIGHT_JOBS_OP = 47;
    public static final int GET_JOB_AND_SQL_SUMMARY_LIST_OP = 48;
    public static final int WRAPPING_PROCESSOR_META_SUPPLIER = 49;
    public static final int WRAPPING_PROCESSOR_SUPPLIER = 50;
    public static final int GET_JOB_USER_CANCELLED_OP = 51;
    public static final int UPLOAD_JOB_METADATA_OP = 52;
    public static final int UPLOAD_JOB_MULTIPART_OP = 53;
    public static final int UPDATE_JOB_CONFIG_OP = 54;

    public static final int FACTORY_ID = FactoryIdHelper.getFactoryId(JET_IMPL_DS_FACTORY, JET_IMPL_DS_FACTORY_ID);

    @Override
    public int getFactoryId() {
        return FACTORY_ID;
    }

    @Override
    public DataSerializableFactory createFactory() {
        return new Factory();
    }

    private static class Factory implements DataSerializableFactory {
        @Override
        public IdentifiedDataSerializable create(int typeId) {
            return switch (typeId) {
                case EXECUTION_PLAN -> new ExecutionPlan();
                case EDGE_DEF -> new EdgeDef();
                case VERTEX_DEF -> new VertexDef();
                case JOB_RECORD -> new JobRecord();
                case JOB_RESULT -> new JobResult();
                case INIT_EXECUTION_OP -> new InitExecutionOperation();
                case START_EXECUTION_OP -> new StartExecutionOperation();
                case JOB_STATUS_EVENT -> new JobStatusEvent();
                case SUBMIT_JOB_OP -> new SubmitJobOperation();
                case GET_JOB_STATUS_OP -> new GetJobStatusOperation();
                case SNAPSHOT_PHASE1_OPERATION -> new SnapshotPhase1Operation();
                case JOB_EXECUTION_RECORD -> new JobExecutionRecord();
                case SESSION_WINDOW_P_WINDOWS -> new SessionWindowP.Windows<>();
                case SLIDING_WINDOW_P_SNAPSHOT_KEY -> new SnapshotKey();
                case GET_JOB_IDS -> new GetJobIdsOperation();
                case JOIN_SUBMITTED_JOB -> new JoinSubmittedJobOperation();
                case UPDATE_JOB_EXECUTION_RECORD_EP -> new UpdateJobExecutionRecordEntryProcessor();
                case TERMINATE_EXECUTION_OP -> new TerminateExecutionOperation();
                case FILTER_JOB_RESULT_BY_NAME -> new FilterJobResultByNamePredicate();
                case GET_JOB_IDS_RESULT -> new GetJobIdsResult();
                case GET_JOB_SUBMISSION_TIME_OP -> new GetJobSubmissionTimeOperation();
                case GET_JOB_CONFIG_OP -> new GetJobConfigOperation();
                case TERMINATE_JOB_OP -> new TerminateJobOperation();
                case ASYNC_SNAPSHOT_WRITER_SNAPSHOT_DATA_KEY -> new AsyncSnapshotWriterImpl.SnapshotDataKey();
                case ASYNC_SNAPSHOT_WRITER_SNAPSHOT_DATA_VALUE_TERMINATOR ->
                        AsyncSnapshotWriterImpl.SnapshotDataValueTerminator.INSTANCE;
                case SNAPSHOT_PHASE1_RESULT -> new SnapshotPhase1Result();
                case RESUME_JOB_OP -> new ResumeJobOperation();
                case NOTIFY_MEMBER_SHUTDOWN_OP -> new NotifyMemberShutdownOperation();
                case GET_JOB_SUMMARY_LIST_OP -> new GetJobSummaryListOperation();
                case JOB_SUMMARY -> new JobSummary();
                case SNAPSHOT_STATS -> new SnapshotStats();
                case PREPARE_FOR_PASSIVE_CLUSTER_OP -> new PrepareForPassiveClusterOperation();
                case SNAPSHOT_VALIDATION_RECORD -> new SnapshotValidationRecord();
                case ADD_JOB_STATUS_LISTENER_OP -> new AddJobStatusListenerOperation();
                case UPDATE_JOB_CONFIG_OP -> new UpdateJobConfigOperation();
                case GET_JOB_METRICS_OP -> new GetJobMetricsOperation();
                case GET_LOCAL_JOB_METRICS_OP -> new GetLocalExecutionMetricsOperation();
                case SNAPSHOT_PHASE2_OPERATION -> new SnapshotPhase2Operation();
                case WRITE_FILE_P_FILE_ID -> new WriteFileP.FileId();
                case JOB_SUSPENSION_CAUSE -> new JobSuspensionCauseImpl();
                case GET_JOB_SUSPENSION_CAUSE_OP -> new GetJobSuspensionCauseOperation();
                case PROCESSOR_SUPPLIER_FROM_SIMPLE_SUPPLIER -> new ProcessorSupplierFromSimpleSupplier();
                case NOOP_PROCESSOR_SUPPLIER -> new NoopP.NoopPSupplier();
                case CHECK_LIGHT_JOBS_OP -> new CheckLightJobsOperation();
                case GET_JOB_AND_SQL_SUMMARY_LIST_OP -> new GetJobAndSqlSummaryListOperation();
                case WRAPPING_PROCESSOR_META_SUPPLIER -> new WrappingProcessorMetaSupplier();
                case WRAPPING_PROCESSOR_SUPPLIER -> new WrappingProcessorSupplier();
                case UPLOAD_JOB_METADATA_OP -> new UploadJobMetaDataOperation();
                case UPLOAD_JOB_MULTIPART_OP -> new UploadJobMultiPartOperation();
                case GET_JOB_USER_CANCELLED_OP -> new IsJobUserCancelledOperation();
                default -> throw new IllegalArgumentException("Unknown type id " + typeId);
            };
        }
    }
}
