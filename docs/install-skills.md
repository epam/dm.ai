# Selective skill installs

DMTools keeps runtime routing out of the core CLI. The installer can still persist a selected skill set so operators can expose only the surfaces they want through an external adapter.

## Installer selection

Use either the environment variable or the CLI flag:

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install | DMTOOLS_SKILLS=jira,github bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install | bash -s -- --skills=jira,github
```

When piping the installer into `bash`, set `DMTOOLS_SKILLS` on the `bash` process (the right side of the pipe) or export it before running the command.

The installer normalizes values to lowercase, trims whitespace, and skips unknown names with a warning. Empty input or `all` keeps the default full install.

To make unknown skill names fail the run instead of being skipped, enable strict mode:

```bash
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install | bash -s -- --skills=jira --strict
curl -fsSL https://github.com/epam/dm.ai/releases/latest/download/install | DMTOOLS_SKILLS=jira DMTOOLS_STRICT_INSTALL=true bash
```

## Routing convention

Expose skill-specific adapters under `/dmtools/{skill}`. Examples:

- `/dmtools/jira`
- `/dmtools/github`
- `/dmtools/xray`

Recommended discovery endpoint:

- `GET /dmtools/endpoints`

Runtime routing is out of scope for this change. DMTools core remains CLI-first; operators should implement the HTTP surface in their own gateway or sidecar.

## Example Nginx routing

```nginx
location /dmtools/endpoints {
    proxy_pass http://dmtools-adapter/endpoints;
}

location ~ ^/dmtools/(?<skill>[a-z0-9_-]+)$ {
    proxy_set_header X-DMTools-Skill $skill;
    proxy_pass http://dmtools-adapter/$skill;
}
```

## Operational note

If backward compatibility with legacy aliases such as `/dmtools-jira` is required, implement those aliases as redirects or gateway rewrites to `/dmtools/{skill}` outside of dmtools-core.
