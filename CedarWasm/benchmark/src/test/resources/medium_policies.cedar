permit(principal == User::"alice", action == Action::"View_Photo", resource);

permit(principal, action == Action::"View_Photo", resource in Album::"vacation");

forbid(principal, action, resource) when { resource.private };

permit(principal, action == Action::"Edit_Photo", resource) when { principal == resource.owner };

permit(principal, action == Action::"Delete_Photo", resource) when { principal == resource.owner };
