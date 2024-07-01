@id("Policy #1")
permit (
  principal in UserGroup::"friends",
  action == Action::"view",
  resource == Photo::"Husky.jpg"
);

@id("Policy #2")
forbid (
  principal == User::"Matt",
  action,
  resource == Photo::"Husky.jpg"
);