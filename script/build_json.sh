#! /bin/sh

set -e

out_dir="./out/json"
file_name="ae_json.jar"

if [ -n "$1" ]; then
    out_dir=$1
fi

echo "building jar..."
javac JSON.java -d "$out_dir"; jar -cvf "${out_dir}/${file_name}" -C "$out_dir" .
