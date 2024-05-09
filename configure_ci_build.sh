#!/bin/bash

cargo_file=CedarJavaFFI/Cargo.toml


sed -i -e '/\[dependencies.cedar-policy/,+4d' $cargo_file

echo "" >> $cargo_file
echo "[dependencies.cedar-policy]" >> $cargo_file
echo "path = \"../cedar/cedar-policy\"" >> $cargo_file
