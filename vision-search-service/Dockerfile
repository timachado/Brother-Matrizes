FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements-core.txt requirements-model.txt ./
RUN pip install --no-cache-dir -r requirements-model.txt
COPY server.py .
RUN useradd --create-home brother && mkdir -p /data /models && chown -R brother:brother /data /app
USER brother
EXPOSE 8000
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000"]
