use mongodb::bson::doc;
use tracing::{event, Level};
use crate::{
  business::repositories::{APP_DB_CONTEXT, REPOSITORIES_RUNTIME},
  types::model::deployment::Deployment, Error,
  utils::runtime_helpers::RuntimeSpawnHandled
};

pub async fn find_by_host(host: String) -> Option<Deployment> {
  event!(Level::DEBUG, "Finding deployment by host {}", &host);

  let host_clone = host.clone();
  let future = 
    async move {
      let result = APP_DB_CONTEXT.deployments
        .find_one(doc! { "host": &host }, None).await;

      let result = result
        .map_err(Error::from) // MongoDB Error
        .map(|d| d.ok_or(Error::from("Deployment not found"))) // 'None' Error
        .and_then(|inner| inner); // Flatten

      result
    };

  let result = REPOSITORIES_RUNTIME.spawn_handled("repositories::deployments::find_by_host", future).await;

  result.map(|r| {
    r.map_or_else(|e| {
      event!(Level::ERROR, "Failed to find deployment with host {}: {}", host_clone, e);
      None
    }, |d| Some(d))
  }).and_then(|d| d)
}