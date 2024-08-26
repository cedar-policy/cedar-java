@id("Policy #1")
permit (
  principal == User::"Aaron",
  action == Action::"view",
  resource == Photo::"SomePhoto.jpg"
);

@id("Policy #2")
permit (principal == User::"Josh", action == Action::"comment", resource == Photo::"SomePhoto.jpg");

@id("Template #1")
permit (
  principal in ?principal,
  action in [Action::"view", Action::"comment"],
  resource in ?resource
)
unless {
  resource.tag == "private"
};