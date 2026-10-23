"""Envia somente ícones iniciais ausentes; nunca sobrescreve objetos existentes.

Requer boto3 e as variáveis MINIO_PUBLIC_ENDPOINT, MINIO_ACCESS_KEY,
MINIO_SECRET_KEY; MINIO_BUCKET_PREFIX=allset e MINIO_REGION=us-east-1 por padrão.
As credenciais são lidas do ambiente e não são exibidas.
"""
import os
from pathlib import Path
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

def seed():
    client = boto3.client('s3', endpoint_url=os.environ['MINIO_PUBLIC_ENDPOINT'],
        aws_access_key_id=os.environ['MINIO_ACCESS_KEY'],
        aws_secret_access_key=os.environ['MINIO_SECRET_KEY'],
        region_name=os.environ.get('MINIO_REGION', 'us-east-1'),
        config=Config(signature_version='s3v4', s3={'addressing_style':'path'}))
    bucket = os.environ.get('MINIO_BUCKET_PREFIX', 'allset') + '-catalog-icons'
    root = Path(__file__).resolve().parents[1] / 'src/main/resources'
    uploaded = skipped = 0
    for file in sorted((root / 'catalog').rglob('*.png')):
        key = file.relative_to(root).as_posix()
        try:
            client.head_object(Bucket=bucket, Key=key)
            skipped += 1
        except ClientError as error:
            if error.response['ResponseMetadata']['HTTPStatusCode'] != 404:
                raise
            client.put_object(Bucket=bucket, Key=key, Body=file.read_bytes(),
                              ContentType='image/png', CacheControl='public, max-age=86400')
            uploaded += 1
    print(f'Ícones enviados: {uploaded}; já existentes preservados: {skipped}.')

if __name__ == '__main__':
    seed()
