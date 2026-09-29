# Brother Matrizes — checklist de Release Candidate

Este checklist separa o que pode ser validado automaticamente do que precisa de aparelho, bordadeira ou segredo de produção.

## Bloqueadores antes da RC

- [ ] Instalar a última build em pelo menos dois aparelhos Android compatíveis.
- [ ] Abrir PES, DST e JEF reais, editar, salvar, reabrir, converter e simular.
- [ ] Criar nomes com fontes internas e TTF/OTF importadas.
- [ ] Forçar fechamento do app durante um trabalho e confirmar recuperação do autossalvamento.
- [ ] Testar pouco espaço disponível e confirmar erro sem corrupção do projeto anterior.
- [ ] Testar backup e restauração da Biblioteca.
- [ ] Testar Google Login em instalação limpa e após sessão expirada.
- [ ] Testar modo sem internet na Minha Conta; a sessão não deve ser apresentada como encerrada.
- [ ] Testar USB OTG/pendrive real.
- [ ] Remover o OTG durante a gravação e confirmar que o envio não entra como concluído.
- [ ] Testar pelo menos uma bordadeira real com PES/DST/JEF compatível.
- [ ] Validar Wi-Fi apenas em modelo/protocolo explicitamente suportado.

## Segurança

- [x] Sem service role/segredo Supabase dentro do APK.
- [x] Tráfego HTTP em texto claro desabilitado.
- [x] Backup automático do Android desabilitado para não migrar sessão/tokens.
- [x] Biblioteca usa backup próprio controlado pelo usuário.
- [x] Operações de assinatura continuam protegidas no backend.
- [ ] Ativar proteção de senha vazada no Supabase Auth antes do lançamento comercial.
- [ ] Rotacionar qualquer segredo que tenha sido exposto fora do ambiente seguro.

## Build de produção

- [x] CI executa testes unitários.
- [x] CI executa Android Lint.
- [x] CI compila APK debug para testes.
- [x] CI compila variante release sem assinatura para detectar regressões de build.
- [x] CI possui workflow manual de assinatura de produção preparado para usar secrets sem versionar a keystore.
- [x] CI executa smoke test instrumentado de inicialização e navegação em Android API 35.
- [ ] Criar keystore de produção fora do repositório.
- [ ] Guardar keystore/senhas em secrets do CI ou cofre seguro.
- [ ] Assinar a variante release com a chave definitiva.
- [ ] Registrar e guardar SHA-256 da chave de assinatura.
- [ ] Testar atualização por cima de uma versão anterior assinada com a mesma chave.
- [ ] Definir URL oficial de atualização/download no site Brother Matrizes.

A RC não deve ser declarada concluída enquanto os itens físicos e a assinatura de produção permanecerem pendentes.
