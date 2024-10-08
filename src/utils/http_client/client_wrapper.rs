use crate::utils::Error;
use reqwest::header::HeaderMap;
use reqwest::{Client, Method, Response};
use serde::Serialize;
use tracing::{event, Level};
use url::Url;

pub struct ClientWrapper {
  pub(crate) client: Client,
  pub(crate) uri: Url,
  pub(crate) headers: HeaderMap,
}
impl ClientWrapper {
  pub(crate) fn set_new_headers(&mut self, headers: HeaderMap) {
    let mut old_headers = self.headers.clone();
    old_headers.extend(headers);
    self.headers = old_headers;
  }

  pub(crate) async fn request<T: Serialize + Sized + Send + Sync>(
    &self,
    method: Method,
    route: &str,
    body: Option<&T>,
  ) -> Result<Response, Error> {
    let uri = self.uri.join(route).map_err(Error::from)?;
    event!(Level::DEBUG, "Sending request to: {uri}");

    let client = self
      .client
      .request(method, uri)
      .headers(self.headers.clone());

    let req = match body {
      Some(body) => client.json(body),
      None => client,
    };

    req.send().await.map_err(Error::from)
  }
}
