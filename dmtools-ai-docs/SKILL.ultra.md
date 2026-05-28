# SKILL.md -> ultra prompt-kernel

```text
DMTΣ{v:1.0.23;lic:Apache-2.0;repo:epam/dm.ai;url:https://github.com/epam/dm.ai;docs:dmtools-ai-docs;sys:enterprise_dark_factory;compat:J17+,mac,linux,wsl}

USE{install,configure,troubleshoot,dmtools.env,jira,ado,figma,confluence,teams,ai,js-agents,testgen,reporting,teammate,ciRunUrl}

BOOT? user∈{1st_dmtools,use_dmtools_feature,"dmtools: command not found","how install dmtools"} => SETUP*
SETUP={
  1:install/update=>curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install.sh | bash;
  2:check=>which dmtools || echo "DMtools not installed";
  3:check=>ls dmtools.env ~/.dmtools.env 2>/dev/null || echo "No dmtools.env found";
  4:check=>grep -q "dmtools.env\|dmtools-local.env" .gitignore 2>/dev/null || echo "Not in .gitignore";
  5:check=>Java17+;
  6:test=>dmtools list | head -5
}

MISS{
  !dmtools=>offer/install;
  !env=>ask{integrations,ai_provider}->mk(dmtools.env);
  !gitignore=>+{"dmtools.env","dmtools-local.env",".dmtools/"}
}

SKILLS{focused:DMTOOLS_SKILLS=jira,github}

ENV{
  jira:JIRA_BASE_PATH,JIRA_EMAIL,JIRA_API_TOKEN,JIRA_AUTH_TYPE=Basic;
  ado:ADO_BASE_PATH,ADO_PAT_TOKEN;
  ai:GEMINI_API_KEY|OPENAI_API_KEY|BEDROCK_ACCESS_KEY_ID+BEDROCK_SECRET_ACCESS_KEY;
  def:DEFAULT_LLM=gemini,DEFAULT_TRACKER=jira
}

TOKENS{
  Jira:https://id.atlassian.com/manage-profile/security/api-tokens;
  Gemini:https://aistudio.google.com/app/apikey;
  OpenAI:https://platform.openai.com/api-keys;
  ADO:https://dev.azure.com -> User Settings -> Personal Access Tokens
}

CMD{
  dmtools list;
  dmtools jira_get_ticket PROJ-123;
  dmtools run agents/config.json;
  dmtools run agents/config.json --ciRunUrl "https://ci.example.com/runs/42";
  dmtools run agents/config.json "${ENCODED_CONFIG}" --inputJql "key=PROJ-1"
}

MCP{all:152+;int:16;Jira:52;Teams:30;Confluence:17;ADO:31;Figma:12;AI:12;KB:5;File:4;Mermaid:3;SharePoint:2;CLI:1}
TOOLS{jira_get_ticket,jira_search_by_jql,jira_xray_create_test,ado_get_work_item,ado_move_to_state,ado_add_comment,ado_list_prs,ado_get_pr,ado_add_pr_comment,ado_resolve_pr_thread,ado_merge_pr,figma_get_layers,figma_get_icons,figma_download_node_image,teams_send_message,teams_messages_since,teams_download_file,gemini_ai_chat,openai_ai_chat,openai_ai_chat_with_files,bedrock_ai_chat}

JS{
  rule:all_MCP_tools_direct_as_functions;
  ex:function action(p){try{const t=jira_get_ticket(p.ticketKey);const a=gemini_ai_chat(`Analyze: ${t.fields.description}`);return{success:true,result:a};}catch(e){return{success:false,error:e.toString()};}}
}

DOCS{
  install:references/installation/README.md;
  install.trouble:references/installation/troubleshooting.md;
  cfg:references/configuration/README.md;
  cfg.formats:references/configuration/cli-output-formats.md;
  cfg.json:references/configuration/json-config-rules.md;
  cfg.jira:references/configuration/integrations/jira.md;
  cfg.ado:references/configuration/integrations/ado.md;
  cfg.gemini:references/configuration/ai-providers/gemini.md;
  jobs:references/jobs/README.md;
  jobs.teammate:references/jobs/README.md#teammate;
  jobs.expert:references/jobs/README.md#expert;
  jobs.tcg:references/jobs/README.md#testcasesgenerator;
  jobs.instructions:references/jobs/README.md#instructionsgenerator;
  jobs.jsrunner:references/jobs/README.md#jsrunner;
  jobs.devreport:references/jobs/README.md#devproductivityreport;
  jobs.bareport:references/jobs/README.md#baproductivityreport;
  jobs.qareport:references/jobs/README.md#qaproductivityreport;
  reporting:references/reporting/report-generation.md;
  jobs.reportviz:references/jobs/README.md#reportvisualizer;
  jobs.kb:references/jobs/README.md#kbprocessingjob;
  agents.best:references/agents/best-practices.md;
  agents.js:references/agents/javascript-agents.md;
  agents.cfg:references/agents/teammate-configs.md;
  agents.cli:references/agents/cli-integration.md;
  testing:references/test-generation/xray-manual.md;
  mcp:references/mcp-tools/README.md;
  ci:references/workflows/github-actions-teammate.md
}

JSON!{
  "name"=exact_Java_class_name;
  ok:{TestCasesGenerator,Teammate,Expert};
  bad:{"My Test Generator","test-generator"};
  why:"TestCasesGenerator"->new TestCasesGenerator()
}

TASKS{
  JiraCfg:echo -n "email@company.com:token" | base64; JIRA_BASE_PATH=https://company.atlassian.net; JIRA_LOGIN_PASS_TOKEN=base64_output_here;
  TestGen:{"name":"TestCasesGenerator","params":{"inputJql":"project = PROJ AND type = Story","testCasesPriorities":"High, Medium, Low","outputType":"creation","testCaseIssueType":"Test","existingTestCasesJql":"project = PROJ AND type = Test","isFindRelated":true,"isGenerateNew":true}};
  JSAgent:function action(params){const ts=jira_search_by_jql(params.jql);for(const t of ts){const r=gemini_ai_chat(`Analyze: ${t.fields.summary}`);jira_post_comment(t.key,r);}return{processed:ts.length};};
  CLIAgent:{"name":"Teammate","params":{"agentParams":{"aiRole":"Senior Software Engineer","instructions":["Implement ticket from input/ folder"]},"cliCommands":["./cicd/scripts/run-cursor-agent.sh \"Read from input/, write to output/\""],"skipAIProcessing":true,"postJSAction":"agents/js/developTicketAndCreatePR.js","inputJql":"key = PROJ-123"}}
}

CI{
  for:{Expert,Teammate,TestCasesGenerator}=>pass --ciRunUrl;
  GH:CI_RUN_URL="${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}";
  ADO:CI_RUN_URL="$(System.TeamFoundationCollectionUri)$(System.TeamProject)/_build/results?buildId=$(Build.BuildId)";
  ticket_flow:{
    1:"Processing started. CI Run: https://...";
    2:normal_result_comment
  };
  override:any --key value after config => patch params block
}

BEST{
  sec:never_commit_credentials;
  ai:start_with_Gemini(free,15req/min);
  test:mock_external_APIs_with_Mockito;
  batch:add_delays_for_rate_limits;
  err:always_try-catch_in_agents
}

TROUBLE{
  "Java 17+ required"->run installer again;
  "401 Unauthorized"->check base64 Jira creds;
  "Rate limit exceeded"->sleep(1000);
  "Field not found"->jira_get_fields
}

ARCH{
  JobSystem:20+ specialized workflow jobs;
  AgentSystem:Java+JavaScript agents;
  ConfigPriority:env > dmtools.env > dmtools-local.env;
  ThreadSafety:JobContext thread-local;
  DI:Dagger2
}

ASSIST{
  proactive_setup_pattern:
    detect_need->run_setup_checks->report{installed?,env?,gitignore?}->ask(integrations)->ask(ai)->create_env->update_gitignore->guide_tokens->test(dmtools list);
  templates:{
    jira_only:JIRA_BASE_PATH=https://company.atlassian.net;JIRA_EMAIL=user@company.com;JIRA_API_TOKEN=token_here;JIRA_AUTH_TYPE=Basic;GEMINI_API_KEY=key_here;DEFAULT_LLM=gemini;DEFAULT_TRACKER=jira;
    ado_only:ADO_BASE_PATH=https://dev.azure.com/org;ADO_PAT_TOKEN=token_here;GEMINI_API_KEY=key_here;DEFAULT_LLM=gemini;DEFAULT_TRACKER=ado;
    both:JIRA_BASE_PATH=https://company.atlassian.net;JIRA_EMAIL=user@company.com;JIRA_API_TOKEN=jira_token;JIRA_AUTH_TYPE=Basic;ADO_BASE_PATH=https://dev.azure.com/org;ADO_PAT_TOKEN=ado_token;GEMINI_API_KEY=key_here;DEFAULT_LLM=gemini;DEFAULT_TRACKER=jira
  };
  if_unclear=>ask_user_first
}
```
