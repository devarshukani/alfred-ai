# Embedding Model

This directory holds the all-MiniLM-L6-v2 ONNX model used for semantic embeddings.

## Required files

1. `all-MiniLM-L6-v2.onnx` — The ONNX model (~22MB)
2. `vocab.txt` — The WordPiece vocabulary

## Setup

Run from the project root:

```bash
./setup-embedding-model.sh
```

This downloads both files from HuggingFace. The app will not start without them.
