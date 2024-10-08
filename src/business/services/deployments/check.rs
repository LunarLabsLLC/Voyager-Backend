use tracing::{event, Level};
use axum::http::StatusCode;

use crate::business::repositories;
use crate::business::services::SERVICES_RUNTIME;
use crate::types::model::deployment::Mode;
use crate::types::other::voyager_error::VoyagerError;
use crate::utils::runtime_helpers::RuntimeSpawnHandled;

pub async fn check(
  host: &str,
  mode: Mode,
  repo_url: String,
  branch: Option<String>,
) -> Result<(), VoyagerError> {
  let final_branch: String;
  let mut log = format!("Checking if deployment exists. Host {host}, mode {mode}, repo_url {repo_url}");
  if let Some(branch) = branch {
    log = format!("{log}, branch {branch}");
    final_branch = branch;
  } else {
    log = format!("{log}, branch default");
    final_branch = "default".to_string();
  }
  event!(Level::INFO, log);

  let host = host.replace('.', "-");
  let future = async move {
    let result = repositories::deployments::find_by_name(&host).await?;
    match result {
      Some(_) => Err(VoyagerError::new(
        format!("Deployment at this subdomain already exists!"),
        StatusCode::BAD_REQUEST,
        true,
        None,
      )),
      None => Ok(()),
    }?;


    if let Mode::Production = mode {
      let result = repositories::deployments::find_by_repo_branch(&repo_url, &final_branch).await?;
      match result {
        Some(_) => Err(VoyagerError::new(
          format!("A Production deployment for this repository and branch already exists!"),
          StatusCode::BAD_REQUEST,
          true,
          None,
        )),
        None => Ok(()),
      }?;
    }

    Ok::<(), VoyagerError>(())
  };

  SERVICES_RUNTIME
    .spawn_handled(
      "services::deployments::check",
      future,
    )
    .await??;

  event!(Level::DEBUG, "Done checking deployment.");

  Ok(())
}
