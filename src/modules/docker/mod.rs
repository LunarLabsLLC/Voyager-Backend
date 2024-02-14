pub mod build_image;
pub mod create_and_start_container;
pub mod delete_container;
pub mod find_internal_port;
pub mod get_logs;
pub mod is_container_running;
pub mod restart_container;
pub mod stop_container_and_delete;
pub mod stop_container;

use crate::Error;

use futures::{executor, TryFutureExt};
use lazy_static::lazy_static;
use tokio::runtime::Runtime;
use bollard::Docker;

lazy_static!(
  #[cfg(unix)]
  pub static ref DOCKER: Docker = executor::block_on(
      DOCKER_RUNTIME
        .spawn_blocking(|| Docker::connect_with_local_defaults()) // Spawns another thread to load Docker
        .map_err(Error::from) // Maps the JoinError type to our Error type
    )
    .map(|r| r.map_err(Error::from)) // Maps the Docker Error type to our Type
    .and_then(|f| f) // Flattens
    .expect("Failed to connect to Docker!");
);

lazy_static!(
  pub static ref DOCKER_RUNTIME: Runtime = Runtime::new().unwrap();
);
