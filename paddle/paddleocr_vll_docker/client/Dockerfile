FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils \
    libglib2.0-0 \
    libgl1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY ocr_pdf.py /app/ocr_pdf.py
COPY ocr_pdf_v1.py /app/ocr_pdf_v1.py
COPY hybrid_pdf_pipeline.py /app/hybrid_pdf_pipeline.py
COPY preprocess_table_lines.py /app/preprocess_table_lines.py
