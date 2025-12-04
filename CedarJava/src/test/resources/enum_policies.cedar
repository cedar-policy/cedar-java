// Valid enum policy from RFC example
permit(
    principal,
    action == Action::"UpdateTask",
    resource
)
when {
    principal == resource.owner &&
    resource.status != Color::"Red"
};

// Valid enum equality comparison
permit(
    principal == User::"alice",
    action == Action::"UpdateTask", 
    resource
)
when {
    resource.status == Color::"Green"
};

// Valid application enum usage
permit(
    principal,
    action == Action::"CreateList",
    resource == Application::"TinyTodo"
);

// Policy with multiple enum comparisons
permit(
    principal,
    action == Action::"UpdateTask",
    resource
)
when {
    resource.status == Color::"Blue" ||
    resource.status == Color::"Green"
};

// Policy testing enum in conditions
forbid(
    principal,
    action == Action::"UpdateTask",
    resource
)
when {
    resource.status == Color::"Red" &&
    principal != resource.owner
};
