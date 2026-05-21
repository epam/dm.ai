# SKILL.md -> SCL example

This file is a **lossy semantic compression** of `SKILL.md` optimized for LLM ingestion.

## Legend

- `D|id|value` - dictionary term
- `E|id|type|k:v|...` - entity/fact
- `R|src|rel|dst` - relation
- `P|flow|step|op|args` - ordered procedure
- `C|scope|rule` - constraint, priority, or policy
- `X|id|example|payload` - example snippet
- `L|id|link|target` - reference link or document path

## Payload

```text
D|a|dmtools
D|b|Comprehensive documentation and assistance for DMTools
D|c|enterprise dark-factory orchestrator
D|d|MCP tools
D|e|Jira
D|f|Azure DevOps
D|g|Figma
D|h|Confluence
D|i|Teams
D|j|test automation
D|k|Java 17+
D|l|macOS
D|m|Linux
D|n|Windows (WSL)
D|o|skill-v1.0.23
D|p|DMtools Team
D|q|https://github.com/epam/dm.ai
D|r|https://github.com/epam/dm.ai/tree/main/dmtools-ai-docs
D|s|first-time setup
D|t|install/update DMtools
D|u|check dmtools command
D|v|check dmtools.env
D|w|check .gitignore
D|x|verify Java installation
D|y|test DMtools with dmtools list
D|z|curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash
D|aa|dmtools.env
D|ab|dmtools-local.env
D|ac|.dmtools/
D|ad|Gemini
D|ae|OpenAI
D|af|AWS Bedrock
D|ag|DEFAULT_LLM=gemini
D|ah|DEFAULT_TRACKER=jira
D|ai|JIRA_BASE_PATH
D|aj|JIRA_EMAIL
D|ak|JIRA_API_TOKEN
D|al|JIRA_AUTH_TYPE=Basic
D|am|ADO_BASE_PATH
D|an|ADO_PAT_TOKEN
D|ao|GEMINI_API_KEY
D|ap|OPENAI_API_KEY
D|aq|BEDROCK_ACCESS_KEY_ID
D|ar|BEDROCK_SECRET_ACCESS_KEY
D|as|use this skill
D|at|installing or configuring DMtools
D|au|setting up integrations
D|av|configuring AI providers
D|aw|developing JavaScript agents
D|ax|generating test cases
D|ay|generating analytics reports
D|az|troubleshooting DMtools issues
D|ba|working with dmtools.env
D|bb|creating AI teammate configurations
D|bc|setting up CI/CD run tracing
D|bd|manual installation
D|be|common commands
D|bf|dmtools list
D|bg|dmtools jira_get_ticket PROJ-123
D|bh|dmtools run agents/config.json
D|bi|ciRunUrl
D|bj|152+ MCP tools
D|bk|16 integrations
D|bl|Jira:52
D|bm|Teams:30
D|bn|Confluence:17
D|bo|ADO:31
D|bp|Figma:12
D|bq|AI Providers:12
D|br|Knowledge Base:5
D|bs|File:4
D|bt|Mermaid:3
D|bu|SharePoint:2
D|bv|CLI:1
D|bw|jira_get_ticket
D|bx|jira_search_by_jql
D|by|jira_xray_create_test
D|bz|ado_get_work_item
D|ca|ado_move_to_state
D|cb|ado_add_comment
D|cc|ado_list_prs
D|cd|ado_get_pr
D|ce|ado_add_pr_comment
D|cf|ado_resolve_pr_thread
D|cg|ado_merge_pr
D|ch|figma_get_layers
D|ci|figma_get_icons
D|cj|figma_download_node_image
D|ck|teams_send_message
D|cl|teams_messages_since
D|cm|teams_download_file
D|cn|gemini_ai_chat
D|co|openai_ai_chat
D|cp|openai_ai_chat_with_files
D|cq|bedrock_ai_chat
D|cr|JavaScript agent pattern
D|cs|all MCP tools are accessible as JavaScript functions
D|ct|detailed documentation
D|cu|JSON Configuration name field is Java class name
D|cv|TestCasesGenerator
D|cw|Teammate
D|cx|Expert
D|cy|do not rename job name field
D|cz|Configure Jira Integration
D|da|Generate Test Cases
D|db|Create JavaScript Agent
D|dc|CI Run Tracing
D|dd|Integrate CLI Agents
D|de|Security: never commit credentials
D|df|AI Provider: start with Gemini
D|dg|Testing: mock external APIs with Mockito
D|dh|Batch Processing: add delays for rate limits
D|di|Error Handling: use try-catch in agents
D|dj|Java 17+ required
D|dk|401 Unauthorized
D|dl|Rate limit exceeded
D|dm|Field not found
D|dn|run installer again
D|do|check base64 encoding of Jira credentials
D|dp|add sleep(1000) between API calls
D|dq|use jira_get_fields to find custom field IDs
D|dr|Job System
D|ds|Agent System
D|dt|Configuration hierarchy
D|du|Thread Safety
D|dv|Dagger 2
D|dw|mentions dmtools first time
D|dx|asks to use DMtools feature
D|dy|gets command not found
D|dz|asks how to install dmtools
D|ea|ask which integrations are needed
D|eb|ask which AI provider is wanted
D|ec|create dmtools.env template
D|ed|add DMtools files to .gitignore
D|ee|guide user to get API tokens
D|ef|Jira only template
D|eg|ADO only template
D|eh|Jira+ADO template
D|ei|ask questions if clarification needed
D|ej|focused skill packages
D|ek|DMTOOLS_SKILLS=jira,github
D|el|token savings up to 70%
D|em|CLI safety v1.7.133+
D|en|ReportGenerator
D|eo|ReportVisualizer
D|ep|KBProcessingJob
D|eq|BAProductivityReport
D|er|QAProductivityReport
D|es|InstructionsGenerator
D|et|JSRunner
D|eu|GitHub Actions teammate workflow

E|skill|doc|name:a|desc:b|license:Apache-2.0|version:o|author:p|repo:q|docs:r
E|product|system|class:c|tool_family:d|integrations:bk|primary_domains:e,f,g,h,i,j
E|compat|runtime|java:k|os:l,m,n
E|setup|workflow|name:s|priority:proactive|recommended:true
E|setup_bootstrap|command|cmd:z|purpose:t
E|checks|list|items:u,v,w,x,y
E|config_template|envfile|primary:aa|secondary:ab|ignore:ac
E|defaults|env|llm:ag|tracker:ah
E|use_cases|scope|items:at,au,av,aw,ax,ay,az,ba,bb,bc
E|manual|procedure|name:bd
E|commands|group|name:be|items:bf,bg,bh
E|capabilities|catalog|tools:bj|integrations:bk
E|counts|breakdown|jira:bl|teams:bm|confluence:bn|ado:bo|figma:bp|ai:bq|kb:br|file:bs|mermaid:bt|sharepoint:bu|cli:bv
E|js_pattern|pattern|name:cr|rule:cs
E|critical_name|constraint|rule:cu|valid:cv,cw,cx|invalid:cy
E|tasks|group|items:cz,da,db,dc,dd
E|best_practices|group|items:de,df,dg,dh,di
E|troubleshooting|group|items:dj,dk,dl,dm
E|architecture|group|items:dr,ds,dt,du,dv
E|auto_setup_triggers|group|items:dw,dx,dy,dz
E|assistant_actions|group|items:ea,eb,ec,ed,ee
E|templates|group|items:ef,eg,eh
E|ask|policy|rule:ei
E|skill_packages|feature|name:ej|value:ek

R|skill|describes|product
R|skill|requires_first|setup
R|setup|starts_with|setup_bootstrap
R|setup|contains|checks
R|skill|uses|config_template
R|skill|sets|defaults
R|skill|applies_to|use_cases
R|skill|offers|manual
R|skill|offers|commands
R|skill|contains|capabilities
R|capabilities|counts_by|counts
R|skill|defines|js_pattern
R|skill|warns|critical_name
R|skill|documents|tasks
R|skill|recommends|best_practices
R|skill|documents|troubleshooting
R|skill|documents|architecture
R|assistant_actions|triggered_by|auto_setup_triggers
R|assistant_actions|uses|templates

P|setup|1|run|z
P|setup|2|check|which dmtools || echo "DMtools not installed"
P|setup|3|check|ls dmtools.env ~/.dmtools.env 2>/dev/null || echo "No dmtools.env found"
P|setup|4|check|grep -q "dmtools.env\\|dmtools-local.env" .gitignore 2>/dev/null || echo "Not in .gitignore"
P|setup|5|check|verify Java installation
P|setup|6|run|dmtools list | head -5

C|setup|when user mentions DMtools or asks to use it perform setup checks immediately
C|setup|if dmtools missing => offer/install using z
C|setup|if aa missing => ask ea + ask eb + do ec + do ed
C|security|aa contains secrets => never commit
C|gitignore|required_lines:dmtools.env,dmtools-local.env,.dmtools/
C|defaults|recommended_ai_provider:ad
C|gemini|note:free_tier_15_req_min
C|json_config|name field is immutable Java class name, not friendly label
C|json_config|allowed examples:cv,cw,cx
C|json_config|disallowed examples:My Test Generator,test-generator
C|ci|for cw,cx,cv pass --ciRunUrl to link ticket comments to pipeline run
C|cli_override|any --key value after config patches params block
C|agents|use try-catch in JavaScript agents
C|user_interaction|if clarification needed ask before proceeding
C|output_formats|json/toon/mini provide el
C|skill_packages|install only required integrations with ek

E|env_jira|template|vars:ai,aj,ak,al,ao,ag,ah
E|env_ado|template|vars:am,an,ao,ag|tracker:ado
E|env_both|template|vars:ai,aj,ak,al,am,an,ao,ag,ah
E|env_ai_optional|template|vars:ap,aq,ar
R|config_template|contains|env_jira
R|config_template|contains|env_ado
R|config_template|contains|env_both
R|config_template|contains|env_ai_optional

L|jira_token|link|https://id.atlassian.com/manage-profile/security/api-tokens
L|gemini_key|link|https://aistudio.google.com/app/apikey
L|openai_key|link|https://platform.openai.com/api-keys
L|ado_pat|link|https://dev.azure.com -> User Settings -> Personal Access Tokens
L|installation|link|references/installation/README.md
L|troubleshooting|link|references/installation/troubleshooting.md
L|config_overview|link|references/configuration/README.md
L|cli_formats|link|references/configuration/cli-output-formats.md
L|json_rules|link|references/configuration/json-config-rules.md
L|jira_setup|link|references/configuration/integrations/jira.md
L|ado_setup|link|references/configuration/integrations/ado.md
L|gemini_setup|link|references/configuration/ai-providers/gemini.md
L|jobs_ref|link|references/jobs/README.md
L|teammate_ref|link|references/jobs/README.md#teammate
L|expert_ref|link|references/jobs/README.md#expert
L|tcg_ref|link|references/jobs/README.md#testcasesgenerator
L|instructions_ref|link|references/jobs/README.md#instructionsgenerator
L|jsrunner_ref|link|references/jobs/README.md#jsrunner
L|dev_report|link|references/jobs/README.md#devproductivityreport
L|ba_report|link|references/jobs/README.md#baproductivityreport
L|qa_report|link|references/jobs/README.md#qaproductivityreport
L|report_gen|link|references/reporting/report-generation.md
L|report_viz|link|references/jobs/README.md#reportvisualizer
L|kb_job|link|references/jobs/README.md#kbprocessingjob
L|agent_practices|link|references/agents/best-practices.md
L|js_agents|link|references/agents/javascript-agents.md
L|teammate_cfg|link|references/agents/teammate-configs.md
L|cli_integration|link|references/agents/cli-integration.md
L|test_generation|link|references/test-generation/xray-manual.md
L|mcp_ref|link|references/mcp-tools/README.md
L|gha_teammate|link|references/workflows/github-actions-teammate.md

X|cmds|example|dmtools list ; dmtools jira_get_ticket PROJ-123 ; dmtools run agents/config.json ; dmtools run agents/config.json --ciRunUrl "https://ci.example.com/runs/42"
X|js_agent|example|function action(params){try{const ticket=jira_get_ticket(params.ticketKey);const analysis=gemini_ai_chat(`Analyze: ${ticket.fields.description}`);return {success:true,result:analysis};}catch(error){return {success:false,error:error.toString()};}}
X|jql_agent|example|function action(params){const tickets=jira_search_by_jql(params.jql);for(const ticket of tickets){const result=gemini_ai_chat(`Analyze: ${ticket.fields.summary}`);jira_post_comment(ticket.key,result);}return {processed:tickets.length};}
X|jira_setup|example|echo -n "email@company.com:token" | base64 ; JIRA_BASE_PATH=https://company.atlassian.net ; JIRA_LOGIN_PASS_TOKEN=base64_output_here
X|testcases_config|example|{"name":"TestCasesGenerator","params":{"inputJql":"project = PROJ AND type = Story","testCasesPriorities":"High, Medium, Low","outputType":"creation","testCaseIssueType":"Test","existingTestCasesJql":"project = PROJ AND type = Test","isFindRelated":true,"isGenerateNew":true}}
X|cli_agent_cfg|example|{"name":"Teammate","params":{"agentParams":{"aiRole":"Senior Software Engineer","instructions":["Implement ticket from input/ folder"]},"cliCommands":["./cicd/scripts/run-cursor-agent.sh \"Read from input/, write to output/\""],"skipAIProcessing":true,"postJSAction":"agents/js/developTicketAndCreatePR.js","inputJql":"key = PROJ-123"}}}
X|ci_github|example|CI_RUN_URL="${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}" ; dmtools run agents/teammate.json --ciRunUrl "${CI_RUN_URL}"
X|ci_ado|example|CI_RUN_URL="$(System.TeamFoundationCollectionUri)$(System.TeamProject)/_build/results?buildId=$(Build.BuildId)" ; dmtools run agents/teammate.json --ciRunUrl "${CI_RUN_URL}"
X|proactive_setup|example|detect need -> run checks -> report installed/missing -> ask integrations -> ask AI provider -> create aa -> update .gitignore -> guide token retrieval

E|doc_install|doc|title:Installation Guide|ref:installation
E|doc_troubleshoot|doc|title:Troubleshooting|ref:troubleshooting
E|doc_config|doc|title:Configuration Overview|ref:config_overview
E|doc_cli_formats|doc|title:CLI Output Formats|ref:cli_formats|note:el
E|doc_json|doc|title:JSON Configuration Rules|ref:json_rules
E|doc_jira|doc|title:Jira Setup|ref:jira_setup
E|doc_ado|doc|title:Azure DevOps|ref:ado_setup
E|doc_gemini|doc|title:Gemini AI|ref:gemini_setup
E|doc_jobs|doc|title:Jobs Reference|ref:jobs_ref
E|doc_teammate|doc|title:Teammate|ref:teammate_ref
E|doc_expert|doc|title:Expert|ref:expert_ref
E|doc_tcg|doc|title:TestCasesGenerator|ref:tcg_ref
E|doc_instr|doc|title:InstructionsGenerator|ref:instructions_ref
E|doc_jsrunner|doc|title:JSRunner|ref:jsrunner_ref
E|doc_devreport|doc|title:DevProductivityReport|ref:dev_report
E|doc_bareport|doc|title:BAProductivityReport|ref:ba_report
E|doc_qareport|doc|title:QAProductivityReport|ref:qa_report
E|doc_reportgen|doc|title:ReportGenerator|ref:report_gen
E|doc_reportviz|doc|title:ReportVisualizer|ref:report_viz
E|doc_kb|doc|title:KBProcessingJob|ref:kb_job
E|doc_agents|doc|title:Agent Best Practices|ref:agent_practices
E|doc_jsagents|doc|title:JavaScript Agents|ref:js_agents
E|doc_cfgs|doc|title:Teammate Configs|ref:teammate_cfg|note:em
E|doc_cliint|doc|title:CLI Integration|ref:cli_integration
E|doc_testgen|doc|title:Test Generation|ref:test_generation
E|doc_mcp|doc|title:MCP Tools Reference|ref:mcp_ref
E|doc_gha|doc|title:GitHub Actions|ref:gha_teammate

R|skill|references|doc_install
R|skill|references|doc_troubleshoot
R|skill|references|doc_config
R|skill|references|doc_cli_formats
R|skill|references|doc_json
R|skill|references|doc_jira
R|skill|references|doc_ado
R|skill|references|doc_gemini
R|skill|references|doc_jobs
R|skill|references|doc_teammate
R|skill|references|doc_expert
R|skill|references|doc_tcg
R|skill|references|doc_instr
R|skill|references|doc_jsrunner
R|skill|references|doc_devreport
R|skill|references|doc_bareport
R|skill|references|doc_qareport
R|skill|references|doc_reportgen
R|skill|references|doc_reportviz
R|skill|references|doc_kb
R|skill|references|doc_agents
R|skill|references|doc_jsagents
R|skill|references|doc_cfgs
R|skill|references|doc_cliint
R|skill|references|doc_testgen
R|skill|references|doc_mcp
R|skill|references|doc_gha

E|tr_java|issue|msg:dj|fix:dn
E|tr_auth|issue|msg:dk|fix:do
E|tr_rate|issue|msg:dl|fix:dp
E|tr_field|issue|msg:dm|fix:dq
R|troubleshooting|contains|tr_java
R|troubleshooting|contains|tr_auth
R|troubleshooting|contains|tr_rate
R|troubleshooting|contains|tr_field

E|arch_job|note|name:dr|value:20+ specialized jobs for workflows
E|arch_agent|note|name:ds|value:Java and JavaScript agents for AI tasks
E|arch_cfg|note|name:dt|value:env vars > dmtools.env > dmtools-local.env
E|arch_thread|note|name:du|value:JobContext with thread-local storage
E|arch_di|note|name:dv|value:Dagger 2 for dependency injection
R|architecture|contains|arch_job
R|architecture|contains|arch_agent
R|architecture|contains|arch_cfg
R|architecture|contains|arch_thread
R|architecture|contains|arch_di

P|auto_setup|1|detect|dw or dx or dy or dz
P|auto_setup|2|run|setup
P|auto_setup|3|report|installed/missing status
P|auto_setup|4|ask|integrations needed
P|auto_setup|5|ask|AI provider
P|auto_setup|6|create|dmtools.env template
P|auto_setup|7|append|.gitignore
P|auto_setup|8|guide|API token acquisition
P|auto_setup|9|test|dmtools list

X|jira_only_env|example|JIRA_BASE_PATH=https://company.atlassian.net ; JIRA_EMAIL=user@company.com ; JIRA_API_TOKEN=token_here ; JIRA_AUTH_TYPE=Basic ; GEMINI_API_KEY=key_here ; DEFAULT_LLM=gemini ; DEFAULT_TRACKER=jira
X|ado_only_env|example|ADO_BASE_PATH=https://dev.azure.com/org ; ADO_PAT_TOKEN=token_here ; GEMINI_API_KEY=key_here ; DEFAULT_LLM=gemini ; DEFAULT_TRACKER=ado
X|both_env|example|JIRA_BASE_PATH=https://company.atlassian.net ; JIRA_EMAIL=user@company.com ; JIRA_API_TOKEN=jira_token ; JIRA_AUTH_TYPE=Basic ; ADO_BASE_PATH=https://dev.azure.com/org ; ADO_PAT_TOKEN=ado_token ; GEMINI_API_KEY=key_here ; DEFAULT_LLM=gemini ; DEFAULT_TRACKER=jira

C|coverage|goal|maximize context fidelity while remaining lossy
C|format|goal|optimized for LLM parsing, not human readability
```
