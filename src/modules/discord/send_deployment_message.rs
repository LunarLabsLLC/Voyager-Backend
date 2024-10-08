use axum::http::StatusCode;
use serenity::all::Webhook;
use serenity::builder::CreateEmbed;
use serenity::builder::ExecuteWebhook;
use serenity::http::Http;
use tracing::event;
use tracing::Level;

use crate::configs::environment::DEVELOPMENT;
use crate::configs::environment::DISCORD_WEBHOOK;
use crate::types::model::deployment::Mode;
use crate::types::other::voyager_error::VoyagerError;
use crate::utils::Error;

pub async fn send_deployment_message(
  id: &str,
  name: &str,
  host: &str,
  mode: &Mode,
) -> Result<(), VoyagerError> {
  if *DEVELOPMENT {
    return Ok(());
  }

  event!(
    Level::INFO,
    "Sending deployment discord message for deployment"
  );

  let embed = CreateEmbed::new()
    .title(format!("[New {mode} deployment | {name}](https://{host})",))
    .description(format!("A new {mode} deployment has been created."))
    .field("ID", id, true)
    .field("Docker Container", name, true);
  let builder = ExecuteWebhook::new().username("Voyager API").embed(embed);

  let http = Http::new("");
  let webhook_client = Webhook::from_url(&http, &DISCORD_WEBHOOK)
    .await
    .map_err(|e| VoyagerError::create_discord_client(Box::new(e)))?;

  let msg = webhook_client
    .execute(&http, false, builder)
    .await
    .map_err(|e| VoyagerError::execute_discord_webhook(Box::new(e)))?;

  let message = msg.map_or(String::new(), |msg| {
    format!(" Returned message is: {}", msg.content)
  });

  event!(Level::DEBUG, "Done sending Discord Webhook.{message}");

  Ok(())
}

impl VoyagerError {
  fn create_discord_client(e: Error) -> Self {
    Self::new(
      "Failed to create Discord client".to_string(),
      StatusCode::INTERNAL_SERVER_ERROR,
      true,
      Some(e),
    )
  }

  fn execute_discord_webhook(e: Error) -> Self {
    Self::new(
      "Failed to send Discord webhook".to_string(),
      StatusCode::INTERNAL_SERVER_ERROR,
      true,
      Some(e),
    )
  }
}
