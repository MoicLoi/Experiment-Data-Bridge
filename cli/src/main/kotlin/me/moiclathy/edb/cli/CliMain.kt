package me.moiclathy.edb.cli

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import me.moiclathy.edb.cli.command.EdbCommand
import me.moiclathy.edb.cli.command.PluginCommand
import me.moiclathy.edb.cli.command.PluginInspectCommand
import me.moiclathy.edb.cli.command.PluginListCommand
import me.moiclathy.edb.cli.command.PluginValidateCommand
import me.moiclathy.edb.cli.command.TaskCancelCommand
import me.moiclathy.edb.cli.command.TaskCommand
import me.moiclathy.edb.cli.command.TaskListCommand
import me.moiclathy.edb.cli.command.TaskStatusCommand
import me.moiclathy.edb.cli.command.TaskSubmitCommand
import me.moiclathy.edb.cli.command.TaskTimelineCommand
import me.moiclathy.edb.cli.command.TaskWatchCommand
import me.moiclathy.edb.cli.command.TaskRetryCommand
import me.moiclathy.edb.cli.command.SchedulerCommand
import me.moiclathy.edb.cli.command.SchedulerMetricsCommand
import me.moiclathy.edb.cli.command.SchedulerStartCommand
import me.moiclathy.edb.cli.command.SchedulerStatusCommand
import me.moiclathy.edb.cli.command.SchedulerStopCommand
import me.moiclathy.edb.cli.command.SchedulerWorkersCommand
import me.moiclathy.edb.cli.command.RetentionCleanupCommand
import me.moiclathy.edb.cli.command.RetentionCommand
import me.moiclathy.edb.cli.command.RetentionEstimateCommand
import me.moiclathy.edb.cli.command.RetentionLastCommand
import me.moiclathy.edb.cli.command.RetentionShowCommand
import me.moiclathy.edb.cli.command.AnalysisCommand
import me.moiclathy.edb.cli.command.AnalysisSubmitCommand
import me.moiclathy.edb.cli.command.DataFileCommand
import me.moiclathy.edb.cli.command.DataFileConvertCommand
import me.moiclathy.edb.cli.command.DataFileDownloadCommand
import me.moiclathy.edb.cli.command.DataFileInfoCommand
import me.moiclathy.edb.cli.command.DataFileListCommand
import me.moiclathy.edb.cli.command.DataFileUploadCommand
import me.moiclathy.edb.cli.command.ReportCommand
import me.moiclathy.edb.cli.command.ReportSubmitCommand
import me.moiclathy.edb.cli.command.AnalysisArtifactsCommand
import me.moiclathy.edb.cli.command.AnalysisHandlersCommand
import me.moiclathy.edb.cli.command.DataFileConversionsCommand
import me.moiclathy.edb.cli.command.DataFileTargetsCommand
import me.moiclathy.edb.cli.command.ReportArtifactsCommand
import me.moiclathy.edb.cli.command.ReportDefinitionsCommand
import me.moiclathy.edb.cli.command.DataFileDeleteCommand
import me.moiclathy.edb.cli.command.DataFileOrphanCleanupCommand
import me.moiclathy.edb.cli.command.DataFileOrphanCommand
import me.moiclathy.edb.cli.command.DataFileOrphanEstimateCommand
import me.moiclathy.edb.cli.command.DataFileOrphanLastCommand
import me.moiclathy.edb.cli.command.DataFileOrphanShowCommand
import me.moiclathy.edb.cli.command.DataFileRetentionCleanupCommand
import me.moiclathy.edb.cli.command.DataFileRetentionCommand
import me.moiclathy.edb.cli.command.DataFileRetentionEstimateCommand
import me.moiclathy.edb.cli.command.DataFileRetentionLastCommand
import me.moiclathy.edb.cli.command.DataFileRetentionShowCommand
import me.moiclathy.edb.cli.command.ProjectCommand
import me.moiclathy.edb.cli.command.ProjectCreateCommand
import me.moiclathy.edb.cli.command.ProjectDeleteCommand
import me.moiclathy.edb.cli.command.ProjectInfoCommand
import me.moiclathy.edb.cli.command.ProjectListCommand
import me.moiclathy.edb.cli.command.ProjectUpdateCommand
import me.moiclathy.edb.cli.command.ProjectReloadCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationExportCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationImportCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationShowCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationUpdateCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationValidateCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationHistoryCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationDiffCommand
import me.moiclathy.edb.cli.command.ProjectConfigurationRestoreCommand
import me.moiclathy.edb.cli.command.WorkflowCommand
import me.moiclathy.edb.cli.command.WorkflowListCommand
import me.moiclathy.edb.cli.command.WorkflowValidateCommand
import me.moiclathy.edb.cli.command.OpCommand
import me.moiclathy.edb.cli.command.DeopCommand
import me.moiclathy.edb.cli.command.ProjectMetadataCommand
import me.moiclathy.edb.cli.command.ProjectMetadataShowCommand
import me.moiclathy.edb.cli.command.ProjectMetadataSetCommand
import me.moiclathy.edb.cli.command.ProjectMetadataDeleteCommand
import me.moiclathy.edb.cli.command.ProjectTagCommand
import me.moiclathy.edb.cli.command.ProjectTagListCommand
import me.moiclathy.edb.cli.command.ProjectTagAddCommand
import me.moiclathy.edb.cli.command.ProjectTagRemoveCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotCreateCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotListCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotLatestCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotInfoCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotCompareCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotMergeCommand
import me.moiclathy.edb.cli.command.ProjectSnapshotDeleteCommand
import me.moiclathy.edb.cli.command.TemplateCommand
import me.moiclathy.edb.cli.command.TemplateListCommand
import me.moiclathy.edb.cli.command.TemplateInfoCommand
import me.moiclathy.edb.cli.command.TemplateCreateCommand
import me.moiclathy.edb.cli.command.TemplateUpdateCommand
import me.moiclathy.edb.cli.command.TemplateDeleteCommand
import me.moiclathy.edb.cli.command.WorkerCommand
import me.moiclathy.edb.cli.command.WorkerListCommand
import me.moiclathy.edb.cli.command.WorkerStatusCommand
import me.moiclathy.edb.cli.command.WorkerStartCommand
import me.moiclathy.edb.cli.command.WorkerStopCommand
import me.moiclathy.edb.cli.command.WorkerRestartCommand
import me.moiclathy.edb.cli.command.WorkerLogsCommand
import me.moiclathy.edb.cli.command.AgentCommand
import me.moiclathy.edb.cli.command.AgentProfileCommand
import me.moiclathy.edb.cli.command.AgentProfileListCommand
import me.moiclathy.edb.cli.command.AgentQuotaCommand
import me.moiclathy.edb.cli.command.AgentSessionCommand
import me.moiclathy.edb.cli.command.AgentSessionCreateCommand
import me.moiclathy.edb.cli.command.AgentSessionInfoCommand
import me.moiclathy.edb.cli.command.AgentSessionCloseCommand
import me.moiclathy.edb.cli.command.AgentSessionMessagesCommand
import me.moiclathy.edb.cli.command.AgentSessionSendCommand
import me.moiclathy.edb.cli.command.AgentRunCommand
import me.moiclathy.edb.cli.command.AgentRunStatusCommand
import me.moiclathy.edb.cli.command.AgentRunCancelCommand
import me.moiclathy.edb.cli.command.AgentRunTimelineCommand
import me.moiclathy.edb.cli.command.AgentRunProposalsCommand
import me.moiclathy.edb.cli.command.AgentRunWatchCommand
import me.moiclathy.edb.cli.command.AgentProposalCommand
import me.moiclathy.edb.cli.command.AgentProposalInfoCommand
import me.moiclathy.edb.cli.command.AgentProposalApproveCommand
import me.moiclathy.edb.cli.command.AgentProposalRejectCommand
import me.moiclathy.edb.cli.command.AgentProposalDecisionCommand
import me.moiclathy.edb.cli.command.AgentProposalSubmitCommand
import me.moiclathy.edb.cli.command.AgentAdminCommand
import me.moiclathy.edb.cli.command.AgentAdminProvidersCommand
import me.moiclathy.edb.cli.command.AgentAdminModelMetricsCommand
import me.moiclathy.edb.cli.command.AgentAdminMetricsCommand
import me.moiclathy.edb.cli.command.AgentAdminRetentionCommand
import me.moiclathy.edb.cli.command.AgentAdminRetentionShowCommand
import me.moiclathy.edb.cli.command.AgentAdminRetentionEstimateCommand
import me.moiclathy.edb.cli.command.AgentAdminRetentionCleanupCommand
import me.moiclathy.edb.cli.command.AgentAdminRetentionLastCommand

fun main(args: Array<String>) {
    createCli().main(args)
}

fun createCli(): EdbCommand {
    val root = EdbCommand()
    return root.subcommands(
        OpCommand(root),
        DeopCommand(root),
        TemplateCommand().subcommands(
            TemplateListCommand(root),
            TemplateInfoCommand(root),
            TemplateCreateCommand(root),
            TemplateUpdateCommand(root),
            TemplateDeleteCommand(root)
        ),
        PluginCommand().subcommands(
            PluginListCommand(root),
            PluginInspectCommand(root),
            PluginValidateCommand(root)
        ),
        WorkflowCommand().subcommands(
            WorkflowListCommand(root),
            WorkflowValidateCommand(root)
        ),
        AgentCommand().subcommands(
            AgentProfileCommand().subcommands(AgentProfileListCommand(root)),
            AgentQuotaCommand(root),
            AgentSessionCommand().subcommands(
                AgentSessionCreateCommand(root),
                AgentSessionInfoCommand(root),
                AgentSessionCloseCommand(root),
                AgentSessionMessagesCommand(root),
                AgentSessionSendCommand(root)
            ),
            AgentRunCommand().subcommands(
                AgentRunStatusCommand(root),
                AgentRunCancelCommand(root),
                AgentRunTimelineCommand(root),
                AgentRunProposalsCommand(root),
                AgentRunWatchCommand(root)
            ),
            AgentProposalCommand().subcommands(
                AgentProposalInfoCommand(root),
                AgentProposalApproveCommand(root),
                AgentProposalRejectCommand(root),
                AgentProposalDecisionCommand(root),
                AgentProposalSubmitCommand(root)
            ),
            AgentAdminCommand().subcommands(
                AgentAdminProvidersCommand(root),
                AgentAdminModelMetricsCommand(root),
                AgentAdminMetricsCommand(root),
                AgentAdminRetentionCommand().subcommands(
                    AgentAdminRetentionShowCommand(root),
                    AgentAdminRetentionEstimateCommand(root),
                    AgentAdminRetentionCleanupCommand(root),
                    AgentAdminRetentionLastCommand(root)
                )
            )
        ),
        TaskCommand().subcommands(
            TaskSubmitCommand(root),
            TaskListCommand(root),
            TaskStatusCommand(root),
            TaskCancelCommand(root),
            TaskTimelineCommand(root),
            TaskWatchCommand(root),
            TaskRetryCommand(root)
        ),
        SchedulerCommand().subcommands(
            SchedulerStatusCommand(root),
            SchedulerStartCommand(root),
            SchedulerStopCommand(root),
            SchedulerWorkersCommand(root),
            SchedulerMetricsCommand(root)
        ),
        WorkerCommand().subcommands(
            WorkerListCommand(root),
            WorkerStatusCommand(root),
            WorkerStartCommand(root),
            WorkerStopCommand(root),
            WorkerRestartCommand(root),
            WorkerLogsCommand(root)
        ),
        RetentionCommand().subcommands(
            RetentionShowCommand(root),
            RetentionEstimateCommand(root),
            RetentionCleanupCommand(root),
            RetentionLastCommand(root)
        ),
        DataFileCommand().subcommands(
            DataFileUploadCommand(root),
            DataFileListCommand(root),
            DataFileInfoCommand(root),
            DataFileDownloadCommand(root),
            DataFileConvertCommand(root),
            DataFileTargetsCommand(root),
            DataFileConversionsCommand(root),
            DataFileDeleteCommand(root),
            DataFileOrphanCommand().subcommands(
                DataFileOrphanShowCommand(root),
                DataFileOrphanEstimateCommand(root),
                DataFileOrphanCleanupCommand(root),
                DataFileOrphanLastCommand(root)
            ),
            DataFileRetentionCommand().subcommands(
                DataFileRetentionShowCommand(root),
                DataFileRetentionEstimateCommand(root),
                DataFileRetentionCleanupCommand(root),
                DataFileRetentionLastCommand(root)
            )
        ),
        AnalysisCommand().subcommands(
            AnalysisSubmitCommand(root),
            AnalysisHandlersCommand(root),
            AnalysisArtifactsCommand(root)
        ),
        ReportCommand().subcommands(
            ReportSubmitCommand(root),
            ReportDefinitionsCommand(root),
            ReportArtifactsCommand(root)
        ),
        ProjectCommand().subcommands(
            ProjectListCommand(root),
            ProjectInfoCommand(root),
            ProjectCreateCommand(root),
            ProjectDeleteCommand(root),
            ProjectUpdateCommand(root),
            ProjectReloadCommand(root),
            ProjectMetadataCommand().subcommands(
                ProjectMetadataShowCommand(root),
                ProjectMetadataSetCommand(root),
                ProjectMetadataDeleteCommand(root)
            ),
            ProjectTagCommand().subcommands(
                ProjectTagListCommand(root),
                ProjectTagAddCommand(root),
                ProjectTagRemoveCommand(root)
            ),
            ProjectSnapshotCommand().subcommands(
                ProjectSnapshotCreateCommand(root),
                ProjectSnapshotListCommand(root),
                ProjectSnapshotLatestCommand(root),
                ProjectSnapshotInfoCommand(root),
                ProjectSnapshotCompareCommand(root),
                ProjectSnapshotMergeCommand(root),
                ProjectSnapshotDeleteCommand(root)
            ),
            ProjectConfigurationCommand().subcommands(
                ProjectConfigurationShowCommand(root),
                ProjectConfigurationValidateCommand(root),
                ProjectConfigurationUpdateCommand(root),
                ProjectConfigurationExportCommand(root),
                ProjectConfigurationImportCommand(root),
                ProjectConfigurationHistoryCommand(root),
                ProjectConfigurationDiffCommand(root),
                ProjectConfigurationRestoreCommand(root)
            )
        )
    )
}
