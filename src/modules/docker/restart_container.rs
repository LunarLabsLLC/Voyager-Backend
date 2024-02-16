use bollard::container::StartContainerOptions;
use tracing::{event, Level};

use crate::{
  modules::docker::{DOCKER, DOCKER_RUNTIME},
  utils::runtime_helpers::RuntimeSpawnHandled,
};

async fn restart_container(container_name: String) -> bool {
  event!(
    Level::INFO,
    "Restarting container with name: {}",
    container_name
  );

  let result = DOCKER_RUNTIME
    .spawn_handled("modules::docker::restart_container", async move {
      DOCKER.restart_container(&container_name, None).await
    })
    .await
    .map_or_else(
      || false,
      |r| {
        r.map_or_else(
          |e| {
            event!(Level::ERROR, "Failed to restart container: {e}");
            false
          },
          |()| true,
        )
      },
    );

  event!(Level::DEBUG, "Done restarting container.");

  result
}
