use serde_json::Value;
use tracing::{event, Level};

use crate::configs::environment::{CLOUDFLARE_ZONE, DEVELOPMENT};
use crate::modules::cloudflare::types::add_dns_record::{Failure, Success};
use crate::modules::cloudflare::types::cloudflare_responses::CloudflareError;
use crate::modules::cloudflare::types::dns_record::DnsRecord;
use crate::modules::cloudflare::CLOUDFLARE_CLIENT;
use crate::types::model::deployment::Mode;
use crate::utils::http_client::ensure_success::EnsureSuccess;
use crate::utils::{to_Err, to_Exc, to_Ok, Error, ResultEx};

pub async fn add_dns_record(host: &str, ip: &str, mode: &Mode) -> ResultEx<String, Vec<Error>> {
  if *DEVELOPMENT {
    return to_Ok("devDnsRecord".to_string());
  }

  event!(
    Level::INFO,
    "Adding DNS record to Cloudflare for host: {}, ip: {}, mode: {:?}",
    host,
    ip,
    mode
  );

  let dns_record = DnsRecord {
    content: ip.to_string(),
    name: host.to_string(),
    proxied: true,
    record_type: "A".to_string(),
    ttl: 1,
    comment: format!("Voyager {mode:?} for {host}"),
  };

  let route = format!("zones/{}/dns_records", *CLOUDFLARE_ZONE);
  let (is_success, response, status) = CLOUDFLARE_CLIENT
    .write()
    .await
    .post::<Value>(route.as_str(), Some(&dns_record))
    .await
    .ensure_success(false);
  if !is_success {
    event!(
      Level::ERROR,
      "Failed to send request to Add DNS Record with Cloudflare."
    );
    return to_Err(vec![]);
  }

  // These are already checked by the .ensure_success(false) + is_success checks above
  #[allow(clippy::unwrap_used)]
  let response = response.unwrap().data().unwrap();
  #[allow(clippy::unwrap_used)]
  let status = status.unwrap();

  event!(Level::DEBUG, "Request sent to Cloudflare");

  let json = serde_json::from_value::<Success>(response.clone());
  if let Ok(success) = json {
    let id = success.result.id;
    event!(
      Level::DEBUG,
      "Cloudflare request was successful with id: {}",
      id
    );
    to_Ok(id)
  } else {
    let failure = serde_json::from_value::<Failure>(response);
    let failure = match failure {
      Ok(failure) => failure,
      Err(err) => {
        event!(
          Level::ERROR,
          "Failed to deserialize failed response for Cloudflare. Status was: {}. Error: {}",
          status,
          err
        );
        return to_Err(vec![]);
      }
    };
    event!(
      Level::DEBUG,
      "Request failed with status {} and errors: {:?}",
      status,
      failure.errors
    );
    to_Err(
      failure
        .errors
        .into_iter()
        .map(|err| Error::from(err))
        .collect(),
    )
  }
}
