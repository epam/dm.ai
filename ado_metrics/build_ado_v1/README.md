# Azure DevOps Data Export Pipeline — v1

Two-stage Python pipeline that pulls work-item data from **Azure DevOps Cloud** and exports it to Excel.

## Setup

```bash
pip install -r requirements.txt
cp env_template ../.env   # fill in ADO_ORG_URL, ADO_PROJECT, ADO_PAT, ADO_TEAM
```

PAT scopes required: **Work Items (Read)**, **Project and Team (Read)**.

## Run

### Stage 1 — Fetch data

```bash
python general_ado_stg01_v1.py
```

Outputs: `ado_raw_issues.json`, `ado_user_map.json`

### Stage 2 — Process and export Excel

```bash
python general_ado_stg02_v1.py
```

Outputs: `tasks_data_YYYYMMDD_HHMMSS.xlsx`

On first run, Stage 2 generates a `Status Categories prepared.xlsx` template — fill in the status mappings and re-run.

## Seeding test data (optional)

See `../seed_tools/README.md`. Quick start:

```bash
cd ../seed_tools
bash run_seed_all.sh
```
