// Organization-wide read access for all members
permit(principal, action == Action::"ViewDocument", resource)
when { resource in principal };

// Project owners get full access
permit(principal, action, resource)
when { resource in Project::"proj1" && principal == Project::"proj1".owner };

// Team leads can manage their team's projects
permit(principal in Team::"engineering", action == Action::"EditProject", resource in Project::"proj1");
permit(principal in Team::"engineering", action == Action::"DeleteProject", resource in Project::"proj1");

// Document owners can edit and delete their own documents
permit(principal, action == Action::"EditDocument", resource)
when { principal == resource.owner };
permit(principal, action == Action::"DeleteDocument", resource)
when { principal == resource.owner };
permit(principal, action == Action::"ShareDocument", resource)
when { principal == resource.owner };

// Comment authors can delete their own comments
permit(principal, action == Action::"DeleteComment", resource)
when { principal == resource.author };

// Anyone in a project can create documents and folders
permit(principal, action == Action::"CreateDocument", resource in Project::"proj1")
when { principal in Department::"engineering" };
permit(principal, action == Action::"CreateFolder", resource in Project::"proj1")
when { principal in Department::"engineering" };

// Pipeline access for CI service accounts
permit(principal == ServiceAccount::"ci-bot", action == Action::"TriggerPipeline", resource in Project::"proj1");
permit(principal == ServiceAccount::"ci-bot", action == Action::"ViewPipeline", resource in Project::"proj1");

// Deployment requires MFA
permit(principal, action == Action::"Deploy", resource)
when { principal.mfaEnabled };

// Secrets require MFA and internal IP
permit(principal, action == Action::"ViewSecret", resource)
when { principal.mfaEnabled && context.sourceIp.isInRange(ip("10.0.0.0/8")) };
permit(principal, action == Action::"EditSecret", resource)
when { principal.mfaEnabled && context.sourceIp.isInRange(ip("10.0.0.0/8")) };

// Audit logs only for org admins
permit(principal in UserGroup::"org-admins", action == Action::"ViewAuditLog", resource);

// Settings management for project admins
permit(principal in UserGroup::"project-admins", action == Action::"EditSetting", resource in Project::"proj1");
permit(principal, action == Action::"ViewSetting", resource in Project::"proj1")
when { principal in Department::"engineering" };

// Forbid access to archived projects
forbid(principal, action, resource in Project::"proj1")
when { Project::"proj1".archived };

// Forbid deployments to locked environments
forbid(principal, action == Action::"Deploy", resource)
when { resource.locked };

// Forbid access from external IPs to classified documents
forbid(principal, action == Action::"ViewDocument", resource)
when { resource.classification == "restricted" && !context.sourceIp.isInRange(ip("10.0.0.0/8")) };
