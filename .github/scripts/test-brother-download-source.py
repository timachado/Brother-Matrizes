#!/usr/bin/env python3
"""Diagnostico publico e de leitura da rota WordPress para o atualizador APK.
Nao faz login, nao envia formularios, nao baixa o binario inteiro e nao usa chaves.
"""
import html
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://timachado.ifree.page/aplicativos/brother-matrizes/"
HEADERS = {"User-Agent": "BrotherMatrizes-Android-Updater/1.10.0",
           "Accept": "application/vnd.android.package-archive, application/octet-stream, text/html, */*"}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None

def inspect(url, max_read=400000):
    request = urllib.request.Request(url, headers=HEADERS, method="GET")
    opener = urllib.request.build_opener(NoRedirect)
    try:
        response = opener.open(request, timeout=15)
    except urllib.error.HTTPError as e:
        response = e
    with response:
        code = response.status
        location = response.headers.get("Location", "")
        kind = response.headers.get("Content-Type", "")
        text = response.read(max_read).decode("utf-8", errors="replace")
    return code, location, kind, text

def main():
    code, location, kind, page = inspect(BASE)
    print(f"Pagina WordPress HTTP={code}, content-type={kind.split(';')[0]}")
    if code != 200:
        print("BLOQUEIO: pagina inacessivel para cliente HTTPS nativo do Android.")
        return 1
    found = []
    for match in re.findall(r"""href\s*=\s*["']([^"']+)["']""", page, re.I):
        url = urllib.parse.urljoin(BASE, html.unescape(match))
        parsed = urllib.parse.urlsplit(url)
        if (parsed.scheme == "https" and parsed.hostname == "timachado.ifree.page"
            and parsed.path == "/" and parsed.port in (None, 443)):
            args = urllib.parse.parse_qs(parsed.query)
            if args.get("ti_app_download") and args.get("_wpnonce"):
                found.append(url)
    if not found:
        print("BLOQUEIO: nao foi encontrado link de APK dinamico na pagina visivel a cliente Android.")
        return 1
    # Nao imprimir nonce/token temporario em logs; somente checar resposta.
    code, location, kind, body = inspect(found[0], 1024)
    print(f"Download WordPress HTTP={code}, content-type={kind.split(';')[0]}")
    if code in (301, 302, 303, 307, 308):
        target = urllib.parse.urljoin(BASE, location)
        parsed = urllib.parse.urlsplit(target)
        if parsed.scheme != "https" or parsed.hostname != "timachado.ifree.page":
            print("BLOQUEIO: redirect sai da hospedagem oficial.")
            return 1
        print("Redirect HTTPS oficial, dependente de suporte a cookies/desafios do host.")
        return 0
    if code != 200 or ("text/html" in kind.lower() and not body.startswith("PK")):
        print("BLOQUEIO: resposta de download nao corresponde a um APK ZIP.")
        return 1
    if not body.startswith("PK"):
        print("BLOQUEIO: conteudo do download nao tem assinatura inicial ZIP/APK.")
        return 1
    print("OK: o site respondeu com os bytes iniciais de um APK para cliente HTTPS nativo.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
