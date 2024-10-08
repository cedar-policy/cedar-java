@id("Proper Policy")
permit (
  principal == User::"Matt",
  action == Action::"view",
  resource == Photo::"Husky.jpg"
);

@id("Malformed Policy")
forbid (
  principal == User::"Liam",
  action,
  resource = Photo::"Husky.jpg"
);
