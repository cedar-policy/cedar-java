permit (
  principal,
  action == Action::"update",
  resource
)
when { resource.owner == principal };
