# MS-AV-02 — Segurança e observabilidade

API de demonstração Spring Boot com autenticação HTTP Basic, bloqueio de login, persistência H2 e indicadores no Prometheus e Grafana. O [plano de implementação](PLANO_IMPLEMENTACAO.md) descreve os contratos e decisões.

## Tecnologias e requisitos

Java 21, Spring Boot 4.1.2, springdoc 3.2.0 e Maven Wrapper; Docker Desktop com Compose para a stack. As imagens usam Eclipse Temurin 21.0.8_9, Prometheus v3.5.0 e Grafana 12.2.0. No Windows, abra PowerShell nesta pasta. Ajuste o caminho do JDK se necessário:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./mvnw.cmd test
```

Os testes usam H2 em memória. Para testar a API manualmente após ela estar pronta, execute `./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'`. Para a validação completa, encerre essa execução e inicie o Docker Desktop; confirme `docker info` antes de executar:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 app
./scripts/testar-api.ps1 -BaseUrl 'http://localhost:8080'
docker compose down
```

Não inicie duas aplicações na porta 8080. `down` preserva os volumes; `down -v` remove os dados persistentes. O roteiro adiciona usuários de demonstração com nomes únicos, sem limpar volumes, e deixa uma conta bloqueada.

## Credenciais e rotas

As contas abaixo são apenas para demonstração local: `admin` / `Admin123!` (ROLE_ADMIN), `manutencao` / `Manut123!` (ROLE_MANUTENCAO) e Grafana `admin` / `Grafana123!`. Admin não herda a role de manutenção. Usuários cadastrados recebem ROLE_USER.

| Método e caminho | Entrada | Acesso | Sucesso |
| --- | --- | --- | --- |
| POST /login | username, senha | público | 200 |
| PUT /trocarsenha/{userid} | atual, nova | público, com senha atual | 204 |
| POST / | username, senha | manutenção | 201 com id e username |
| GET / | — | manutenção | 200 com lista de id e username |
| PUT /unlock/{userid} | — | admin | 204 |

Entradas inválidas retornam 400; falha de autenticação 401; role insuficiente 403; ID inexistente 404; username duplicado 409. IDs são numéricos e positivos; os exemplos usam ID 3 apenas como ilustração, devendo ser substituído pelo ID retornado no cadastro. Senhas aceitam até 72 bytes em UTF-8 para BCrypt.

```powershell
curl.exe -i -X POST http://localhost:8080/ -u 'manutencao:Manut123!' -H 'Content-Type: application/json' -d '{\"username\":\"exemplo\",\"senha\":\"senha1\"}'
curl.exe -i http://localhost:8080/ -u 'manutencao:Manut123!'
curl.exe -i -X POST http://localhost:8080/login -H 'Content-Type: application/json' -d '{\"username\":\"exemplo\",\"senha\":\"senha1\"}'
curl.exe -i -X PUT http://localhost:8080/trocarsenha/3 -H 'Content-Type: application/json' -d '{\"atual\":\"senha1\",\"nova\":\"senha2\"}'
curl.exe -i -X PUT http://localhost:8080/unlock/3 -u 'admin:Admin123!'
```

Duas falhas consecutivas de `POST /login` bloqueiam a conta; um sucesso anterior ao bloqueio zera as falhas. Só o desbloqueio administrativo remove o bloqueio; trocar a senha não o remove. Basic também rejeita bloqueados, mas não altera a contagem de tentativas nem as métricas de login. A API não cria sessão ou token.

Swagger está em http://localhost:8080/swagger-ui/index.html e OpenAPI em http://localhost:8080/v3/api-docs. Use **Authorize** com credenciais Basic apropriadas antes de executar operações restritas. Health e métricas estão em `/actuator/health` e `/actuator/prometheus`.

## Dados e observabilidade

As três tabelas possuem IDs numéricos identity. `usuarios.username` é único; `login_attempts.usuario_id` e `authorities.usuario_id` referenciam `usuarios.id`. O SQL foi corrigido para remover a vírgula final, ampliar o armazenamento do hash BCrypt e incluir `enabled` e `authorities` necessários ao Spring Security JDBC. O banco H2 em arquivo é persistido no volume `app-data`; as contas, senhas e bloqueios sobrevivem ao reinício. Localmente o banco usa `./data/av2db`.

Prometheus em http://localhost:9090 coleta a cada 5 segundos. O [dashboard](http://localhost:3000/d/av2-seguranca) atualiza a cada 5 segundos e mostra `seguranca_logins_validos_total`, `seguranca_logins_invalidos_total` e `seguranca_usuarios_bloqueados`. Os dois contadores somam somente respostas 200 e 401 de `POST /login` desde o início do processo e zeram quando a aplicação reinicia. O gauge consulta o banco e permanece coerente com os bloqueios persistidos.

A [captura do dashboard](docs/grafana/dashboard.png) e o [JSON exportado](docs/grafana/dashboard-exportado.json) são evidências da instância. Para exportar no Grafana, use Share > Export > Export as code. Para importar, use Dashboards > New > Import, selecione o JSON e associe o datasource Prometheus de UID `prometheus`; altere o UID ou escolha outra pasta se quiser evitar substituir o dashboard provisionado.
