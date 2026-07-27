# 🛍️ Mahal Commerce — Backend API

> **API RESTful de alta performance e segurança para ecossistema de E-Commerce e Gestão Comercial.**

O **Mahal Commerce** é um backend enterprise para e-commerce e gestão operacional integrada, desenvolvido em **Java 21** e **Spring Boot 4**. O objetivo do projeto é fornecer uma plataforma escalável, desacoplada e extremamente segura para suportar operações de vendas online e físicas (balcão), controle de estoque, logística, gestão financeira e suprimentos.

---

## 📖 Documentação Completa no Notion

Para manter o repositório enxuto e de fácil navegação, a documentação detalhada sobre arquitetura, modelo de domínio, guia de endpoints, segurança e roadmap está centralizada no Notion:

👉 **[Acessar Central de Documentação no Notion 🚀](https://app.notion.com/p/cernesolution/Backend-Mahal-E-commerce-398700a17d79802a88eaca68efede4c4)**

> **Conteúdos disponíveis no Notion:**
> - 📐 Diagramas e fluxos de Arquitetura Hexagonal
> - 💼 Modelo de Domínio (Vendas, Estoque, Logística, Financeiro, Compras)
> - 🔐 Especificação detalhada de Segurança (JWT, Refresh Tokens, 2FA/TOTP, RBAC)
> - 📡 Referência completa das APIs REST (OpenAPI / Swagger)
> - 🗺️ Roadmap de desenvolvimento e backlog de tarefas

---

## 🏛️ Visão Geral da Arquitetura

O sistema é dividido estritamente em portas e adaptadores, validado via **ArchUnit**:

```text
[ Adapter In (REST Controllers, DTOs) ]
                │
                ▼ (Use Cases)
       [ Core (Domain & Business Logic) ]
                ▲ (Ports & Repositories)
                │
[ Adapter Out (JPA/PostgreSQL, Redis, AWS S3, Mail) ]
```

* **Core isolado:** Não possui dependências do Spring Framework, JPA ou bibliotecas externas.
* **Flexibilidade:** Substituição de infraestrutura (ex: trocar banco ou cache) sem alterar o código de negócio.

---

## 💼 Módulos do Sistema

| Módulo | Descrição |
|---|---|
| 🔐 **Security & Auth** | Autenticação JWT com refresh token opaco no Redis, 2FA TOTP, RBAC granular e Rate Limiting. |
| 🛒 **E-Commerce & Vendas** | Carrinho de compras, checkout, gestão de catálogo e pedidos. |
| 🏪 **Vendas Balcão** | Ponto de venda (PDV) e atendimento presencial. |
| 📦 **Estoque & Logística** | Controle de inventário, movimentações, cálculo de frete e rastreio. |
| 💰 **Financeiro & Compras** | Contas a pagar/receber, conciliação e pedidos de suprimentos junto a fornecedores. |

---

## 🛠️ Stack Tecnológica

* **Core:** Java 21 | Spring Boot 4.0.6 | Spring Security
* **Persistência & Cache:** PostgreSQL 16 | Flyway Migrations | Redis 7
* **Segurança:** JJWT | BCrypt | AES-256-GCM (TOTP secrets)
* **Resiliência & Ops:** ShedLock | Prometheus | Grafana | Docker Compose
* **Qualidade & Testes:** JUnit 5 | Mockito | Testcontainers | ArchUnit

---

## 👨‍💻 Autor & Contato

**Jeff Henryson**
* **Email:** jeffhunbruey@gmail.com
* **Telefone:** (83) 99669-7177
* **GitHub:** [@jeffhenryson](https://github.com/jeffhenryson)
